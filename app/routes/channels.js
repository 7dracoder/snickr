// routes/channels.js
const express = require('express');
const { body, validationResult } = require('express-validator');
const pool   = require('../db');
const { requireLogin } = require('../middleware/auth');
const router = express.Router();

// ── GET /channels/:id
router.get('/:id', requireLogin, async (req, res) => {
  const channelId = parseInt(req.params.id);
  const userId    = req.session.user.user_id;
  try {
    const { rows: [ch] } = await pool.query(
      `SELECT c.*, w.name AS workspace_name, w.workspace_id,
              EXISTS(SELECT 1 FROM channel_memberships cm WHERE cm.channel_id = c.channel_id AND cm.user_id = $2) AS is_member,
              EXISTS(SELECT 1 FROM workspace_admins wa WHERE wa.workspace_id = c.workspace_id AND wa.user_id = $2) AS is_ws_admin
       FROM channels c
       JOIN workspaces w ON w.workspace_id = c.workspace_id
       WHERE c.channel_id = $1`,
      [channelId, userId]
    );
    if (!ch) return res.render('error', { message: 'Channel not found', code: 404, currentUser: req.session.user, flash: null });

    const { rows: [wsMember] } = await pool.query(
      'SELECT 1 FROM workspace_members WHERE workspace_id=$1 AND user_id=$2',
      [ch.workspace_id, userId]
    );
    if (!wsMember) {
      req.session.flash = { type: 'error', message: 'Access denied.' };
      return res.redirect('/workspaces');
    }

    if (ch.channel_type !== 'public' && !ch.is_member) {
      req.session.flash = { type: 'error', message: 'You are not a member of this channel.' };
      return res.redirect(`/workspaces/${ch.workspace_id}`);
    }

    // Messages – last 100
    const { rows: messages } = await pool.query(
      `SELECT m.message_id, m.body, m.posted_at,
              u.user_id, u.username, u.nickname
       FROM messages m
       JOIN users u ON u.user_id = m.sender_id
       WHERE m.channel_id = $1
       ORDER BY m.posted_at ASC
       LIMIT 100`,
      [channelId]
    );

    const { rows: members } = await pool.query(
      `SELECT u.user_id, u.username, u.nickname
       FROM channel_memberships cm
       JOIN users u ON u.user_id = cm.user_id
       WHERE cm.channel_id = $1
       ORDER BY u.username`,
      [channelId]
    );

    const { rows: inviteable } = await pool.query(
      `SELECT u.user_id, u.username
       FROM workspace_members wm
       JOIN users u ON u.user_id = wm.user_id
       WHERE wm.workspace_id = $1
         AND u.user_id NOT IN (SELECT user_id FROM channel_memberships WHERE channel_id = $2)
       ORDER BY u.username`,
      [ch.workspace_id, channelId]
    );

    // All channels in workspace for sidebar
    const { rows: allChannels } = await pool.query(
      `SELECT c.channel_id, c.name, c.channel_type,
              EXISTS(SELECT 1 FROM channel_memberships cm WHERE cm.channel_id = c.channel_id AND cm.user_id = $2) AS is_member
       FROM channels c
       WHERE c.workspace_id = $1
         AND (c.channel_type = 'public'
              OR EXISTS(SELECT 1 FROM channel_memberships cm WHERE cm.channel_id = c.channel_id AND cm.user_id = $2))
       ORDER BY c.channel_type, c.name`,
      [ch.workspace_id, userId]
    );

    res.render('channels/show', { ch, messages, members, inviteable, allChannels });
  } catch (err) {
    console.error(err);
    res.render('error', { message: 'Could not load channel', code: 500, currentUser: req.session.user, flash: null });
  }
});

// ── GET /channels/:id/data – JSON API for SPA channel switching
router.get('/:id/data', requireLogin, async (req, res) => {
  const channelId = parseInt(req.params.id);
  const userId    = req.session.user.user_id;
  try {
    const { rows: [ch] } = await pool.query(
      `SELECT c.*, w.name AS workspace_name, w.workspace_id,
              EXISTS(SELECT 1 FROM channel_memberships cm WHERE cm.channel_id = c.channel_id AND cm.user_id = $2) AS is_member
       FROM channels c
       JOIN workspaces w ON w.workspace_id = c.workspace_id
       WHERE c.channel_id = $1`,
      [channelId, userId]
    );
    if (!ch) return res.status(404).json({ error: 'Not found' });

    const { rows: [wsMember] } = await pool.query(
      'SELECT 1 FROM workspace_members WHERE workspace_id=$1 AND user_id=$2',
      [ch.workspace_id, userId]
    );
    if (!wsMember) return res.status(403).json({ error: 'Access denied' });
    if (ch.channel_type !== 'public' && !ch.is_member) return res.status(403).json({ error: 'Not a member' });

    const { rows: messages } = await pool.query(
      `SELECT m.message_id, m.body, m.posted_at,
              u.user_id, u.username, u.nickname
       FROM messages m
       JOIN users u ON u.user_id = m.sender_id
       WHERE m.channel_id = $1
       ORDER BY m.posted_at ASC LIMIT 100`,
      [channelId]
    );

    const { rows: members } = await pool.query(
      `SELECT u.user_id, u.username, u.nickname
       FROM channel_memberships cm
       JOIN users u ON u.user_id = cm.user_id
       WHERE cm.channel_id = $1 ORDER BY u.username`,
      [channelId]
    );

    const { rows: inviteable } = await pool.query(
      `SELECT u.user_id, u.username
       FROM workspace_members wm
       JOIN users u ON u.user_id = wm.user_id
       WHERE wm.workspace_id = $1
         AND u.user_id NOT IN (SELECT user_id FROM channel_memberships WHERE channel_id = $2)
       ORDER BY u.username`,
      [ch.workspace_id, channelId]
    );

    res.json({ ch, messages, members, inviteable });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Server error' });
  }
});
router.post('/', requireLogin, [
  body('name').trim().isLength({ min: 2, max: 80 })
    .matches(/^[a-zA-Z0-9_-]+$/).withMessage('Channel name: 2–80 chars, letters/numbers/-/_'),
  body('channel_type').isIn(['public', 'private']).withMessage('Invalid channel type'),
  body('workspace_id').isInt().withMessage('workspace_id required'),
], async (req, res) => {
  const errors = validationResult(req);
  const wsId   = parseInt(req.body.workspace_id);
  if (!errors.isEmpty()) {
    req.session.flash = { type: 'error', message: errors.array()[0].msg };
    return res.redirect(`/workspaces/${wsId}`);
  }

  const { name, channel_type } = req.body;
  const userId = req.session.user.user_id;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const { rows: [member] } = await client.query(
      'SELECT 1 FROM workspace_members WHERE workspace_id=$1 AND user_id=$2', [wsId, userId]
    );
    if (!member) throw new Error('Not a workspace member');

    const { rows: [ch] } = await client.query(
      `INSERT INTO channels (workspace_id, name, channel_type, creator_id) VALUES ($1, $2, $3, $4) RETURNING channel_id`,
      [wsId, name, channel_type, userId]
    );
    await client.query('INSERT INTO channel_memberships (channel_id, user_id) VALUES ($1, $2)', [ch.channel_id, userId]);
    await client.query('COMMIT');
    req.session.flash = { type: 'success', message: `#${name} created!` };
    res.redirect(`/channels/${ch.channel_id}`);
  } catch (err) {
    await client.query('ROLLBACK');
    console.error(err);
    req.session.flash = {
      type: 'error',
      message: err.message.includes('unique') ? 'Channel name already exists in this workspace.' : 'Could not create channel.',
    };
    res.redirect(`/workspaces/${wsId}`);
  } finally {
    client.release();
  }
});

// ── POST /channels/dm – start or open a direct message
router.post('/dm', requireLogin, [
  body('target_user_id').isInt().withMessage('Select a user'),
  body('workspace_id').isInt().withMessage('workspace_id required'),
], async (req, res) => {
  const errors = validationResult(req);
  const wsId   = parseInt(req.body.workspace_id);
  if (!errors.isEmpty()) {
    req.session.flash = { type: 'error', message: errors.array()[0].msg };
    return res.redirect(`/workspaces/${wsId}`);
  }

  const userId   = req.session.user.user_id;
  const targetId = parseInt(req.body.target_user_id);

  if (userId === targetId) {
    req.session.flash = { type: 'error', message: "You can't DM yourself." };
    return res.redirect(`/workspaces/${wsId}`);
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // Check if DM already exists between these two users in this workspace
    const { rows: [existing] } = await client.query(
      `SELECT c.channel_id FROM channels c
       JOIN channel_memberships cm1 ON cm1.channel_id = c.channel_id AND cm1.user_id = $1
       JOIN channel_memberships cm2 ON cm2.channel_id = c.channel_id AND cm2.user_id = $2
       WHERE c.workspace_id = $3 AND c.channel_type = 'direct'`,
      [userId, targetId, wsId]
    );

    if (existing) {
      await client.query('ROLLBACK');
      return res.redirect(`/channels/${existing.channel_id}`);
    }

    // Get target username for channel name
    const { rows: [target] } = await client.query('SELECT username FROM users WHERE user_id=$1', [targetId]);
    const myUsername = req.session.user.username;
    const dmName = [myUsername, target.username].sort().join('-');

    const { rows: [ch] } = await client.query(
      `INSERT INTO channels (workspace_id, name, channel_type, creator_id) VALUES ($1, $2, 'direct', $3) RETURNING channel_id`,
      [wsId, dmName, userId]
    );
    await client.query('INSERT INTO channel_memberships (channel_id, user_id) VALUES ($1, $2)', [ch.channel_id, userId]);
    await client.query('INSERT INTO channel_memberships (channel_id, user_id) VALUES ($1, $2)', [ch.channel_id, targetId]);
    await client.query('COMMIT');
    res.redirect(`/channels/${ch.channel_id}`);
  } catch (err) {
    await client.query('ROLLBACK');
    console.error(err);
    req.session.flash = { type: 'error', message: 'Could not open DM.' };
    res.redirect(`/workspaces/${wsId}`);
  } finally {
    client.release();
  }
});

// ── POST /channels/:id/join
router.post('/:id/join', requireLogin, async (req, res) => {
  const channelId = parseInt(req.params.id);
  const userId    = req.session.user.user_id;
  try {
    const { rows: [ch] } = await pool.query('SELECT * FROM channels WHERE channel_id=$1', [channelId]);
    if (!ch || ch.channel_type !== 'public') {
      req.session.flash = { type: 'error', message: 'Cannot join this channel directly.' };
      return res.redirect('back');
    }
    await pool.query(
      `INSERT INTO channel_memberships (channel_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`,
      [channelId, userId]
    );
    res.redirect(`/channels/${channelId}`);
  } catch (err) {
    console.error(err);
    req.session.flash = { type: 'error', message: 'Could not join channel.' };
    res.redirect('back');
  }
});

// ── POST /channels/:id/leave
router.post('/:id/leave', requireLogin, async (req, res) => {
  const channelId = parseInt(req.params.id);
  const userId    = req.session.user.user_id;
  try {
    const { rows: [ch] } = await pool.query('SELECT * FROM channels WHERE channel_id=$1', [channelId]);
    if (!ch) return res.redirect('/workspaces');
    await pool.query('DELETE FROM channel_memberships WHERE channel_id=$1 AND user_id=$2', [channelId, userId]);
    req.session.flash = { type: 'success', message: `Left #${ch.name}.` };
    res.redirect(`/workspaces/${ch.workspace_id}`);
  } catch (err) {
    console.error(err);
    res.redirect('back');
  }
});

// ── POST /channels/:id/invite
router.post('/:id/invite', requireLogin, [
  body('invitee_id').isInt().withMessage('Select a user to invite'),
], async (req, res) => {
  const channelId = parseInt(req.params.id);
  const userId    = req.session.user.user_id;
  const errors    = validationResult(req);
  if (!errors.isEmpty()) {
    req.session.flash = { type: 'error', message: errors.array()[0].msg };
    return res.redirect(`/channels/${channelId}`);
  }

  const inviteeId = parseInt(req.body.invitee_id);
  try {
    const { rows: [member] } = await pool.query(
      'SELECT 1 FROM channel_memberships WHERE channel_id=$1 AND user_id=$2', [channelId, userId]
    );
    if (!member) {
      req.session.flash = { type: 'error', message: 'Only channel members can invite.' };
      return res.redirect(`/channels/${channelId}`);
    }
    await pool.query(
      `INSERT INTO channel_invitations (channel_id, invited_by, invitee_id) VALUES ($1, $2, $3) ON CONFLICT DO NOTHING`,
      [channelId, userId, inviteeId]
    );
    req.session.flash = { type: 'success', message: 'Invitation sent.' };
    res.redirect(`/channels/${channelId}`);
  } catch (err) {
    console.error(err);
    req.session.flash = { type: 'error', message: 'Could not send invitation.' };
    res.redirect(`/channels/${channelId}`);
  }
});

module.exports = router;
