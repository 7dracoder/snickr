// routes/workspaces.js
const express = require('express');
const { body, validationResult } = require('express-validator');
const pool   = require('../db');
const { requireLogin } = require('../middleware/auth');
const router = express.Router();

// ── GET /workspaces
router.get('/', requireLogin, async (req, res) => {
  const userId = req.session.user.user_id;
  try {
    const { rows: workspaces } = await pool.query(
      `SELECT w.workspace_id, w.name, w.description, w.created_at,
              u.username AS creator_name,
              (SELECT COUNT(*) FROM workspace_members wm2 WHERE wm2.workspace_id = w.workspace_id) AS member_count,
              EXISTS(SELECT 1 FROM workspace_admins wa WHERE wa.workspace_id = w.workspace_id AND wa.user_id = $1) AS is_admin
       FROM workspaces w
       JOIN workspace_members wm ON wm.workspace_id = w.workspace_id
       JOIN users u ON u.user_id = w.creator_id
       WHERE wm.user_id = $1
       ORDER BY w.name`,
      [userId]
    );

    // Pending invitation count for badge
    const { rows: [{ count: pendingCount }] } = await pool.query(
      `SELECT COUNT(*) FROM (
         SELECT 1 FROM workspace_invitations WHERE invitee_email=$2 AND status='pending'
         UNION ALL
         SELECT 1 FROM channel_invitations WHERE invitee_id=$1 AND status='pending'
       ) t`,
      [userId, req.session.user.email]
    );

    res.render('workspaces/index', { workspaces, pendingCount: parseInt(pendingCount) });
  } catch (err) {
    console.error(err);
    res.render('error', { message: 'Could not load workspaces', code: 500, currentUser: req.session.user, flash: null });
  }
});

// ── GET /workspaces/new
router.get('/new', requireLogin, (req, res) => {
  res.render('workspaces/new', { errors: [] });
});

// ── POST /workspaces
router.post('/', requireLogin, [
  body('name').trim().isLength({ min: 2, max: 100 }).withMessage('Name must be 2–100 chars'),
  body('description').trim().isLength({ max: 500 }).optional({ checkFalsy: true }),
], async (req, res) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) return res.render('workspaces/new', { errors: errors.array() });

  const { name, description } = req.body;
  const userId = req.session.user.user_id;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const { rows: [ws] } = await client.query(
      `INSERT INTO workspaces (name, description, creator_id) VALUES ($1, $2, $3) RETURNING workspace_id`,
      [name, description || null, userId]
    );
    await client.query('INSERT INTO workspace_members (workspace_id, user_id) VALUES ($1, $2)', [ws.workspace_id, userId]);
    await client.query('INSERT INTO workspace_admins  (workspace_id, user_id) VALUES ($1, $2)', [ws.workspace_id, userId]);
    // Auto-create a #general channel
    const { rows: [ch] } = await client.query(
      `INSERT INTO channels (workspace_id, name, channel_type, creator_id) VALUES ($1, 'general', 'public', $2) RETURNING channel_id`,
      [ws.workspace_id, userId]
    );
    await client.query('INSERT INTO channel_memberships (channel_id, user_id) VALUES ($1, $2)', [ch.channel_id, userId]);
    await client.query('COMMIT');
    req.session.flash = { type: 'success', message: `Workspace "${name}" created!` };
    res.redirect(`/workspaces/${ws.workspace_id}`);
  } catch (err) {
    await client.query('ROLLBACK');
    console.error(err);
    res.render('workspaces/new', { errors: [{ msg: 'Could not create workspace.' }] });
  } finally {
    client.release();
  }
});

// ── GET /workspaces/:id
router.get('/:id', requireLogin, async (req, res) => {
  const wsId   = parseInt(req.params.id);
  const userId = req.session.user.user_id;
  try {
    const { rows: [ws] } = await pool.query(
      `SELECT w.*, u.username AS creator_name,
              EXISTS(SELECT 1 FROM workspace_admins wa WHERE wa.workspace_id = w.workspace_id AND wa.user_id = $2) AS is_admin
       FROM workspaces w
       JOIN workspace_members wm ON wm.workspace_id = w.workspace_id AND wm.user_id = $2
       JOIN users u ON u.user_id = w.creator_id
       WHERE w.workspace_id = $1`,
      [wsId, userId]
    );
    if (!ws) {
      req.session.flash = { type: 'error', message: 'Workspace not found or access denied.' };
      return res.redirect('/workspaces');
    }

    const { rows: channels } = await pool.query(
      `SELECT c.channel_id, c.name, c.channel_type,
              EXISTS(SELECT 1 FROM channel_memberships cm WHERE cm.channel_id = c.channel_id AND cm.user_id = $2) AS is_member
       FROM channels c
       WHERE c.workspace_id = $1
         AND (c.channel_type = 'public'
              OR EXISTS(SELECT 1 FROM channel_memberships cm WHERE cm.channel_id = c.channel_id AND cm.user_id = $2))
       ORDER BY c.channel_type, c.name`,
      [wsId, userId]
    );

    const { rows: members } = await pool.query(
      `SELECT u.user_id, u.username, u.nickname,
              EXISTS(SELECT 1 FROM workspace_admins wa WHERE wa.workspace_id = $1 AND wa.user_id = u.user_id) AS is_admin
       FROM workspace_members wm
       JOIN users u ON u.user_id = wm.user_id
       WHERE wm.workspace_id = $1
       ORDER BY u.username`,
      [wsId]
    );

    // Other workspace members for DM (exclude self)
    const dmTargets = members.filter(m => m.user_id !== userId);

    res.render('workspaces/show', { ws, channels, members, dmTargets });
  } catch (err) {
    console.error(err);
    res.render('error', { message: 'Could not load workspace', code: 500, currentUser: req.session.user, flash: null });
  }
});

// ── POST /workspaces/:id/invite
router.post('/:id/invite', requireLogin, [
  body('invitee_email').isEmail().normalizeEmail().withMessage('Valid email required'),
], async (req, res) => {
  const wsId   = parseInt(req.params.id);
  const userId = req.session.user.user_id;
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    req.session.flash = { type: 'error', message: errors.array()[0].msg };
    return res.redirect(`/workspaces/${wsId}`);
  }

  const { invitee_email } = req.body;
  try {
    const { rows: [admin] } = await pool.query(
      'SELECT 1 FROM workspace_admins WHERE workspace_id=$1 AND user_id=$2', [wsId, userId]
    );
    if (!admin) {
      req.session.flash = { type: 'error', message: 'Only admins can invite members.' };
      return res.redirect(`/workspaces/${wsId}`);
    }

    const { rows: [invitee] } = await pool.query('SELECT user_id FROM users WHERE email=$1', [invitee_email]);

    // Check already a member
    if (invitee) {
      const { rows: [alreadyMember] } = await pool.query(
        'SELECT 1 FROM workspace_members WHERE workspace_id=$1 AND user_id=$2', [wsId, invitee.user_id]
      );
      if (alreadyMember) {
        req.session.flash = { type: 'error', message: 'That user is already a member.' };
        return res.redirect(`/workspaces/${wsId}`);
      }
    }

    const { rows: [existing] } = await pool.query(
      `SELECT 1 FROM workspace_invitations WHERE workspace_id=$1 AND invitee_email=$2 AND status='pending'`,
      [wsId, invitee_email]
    );
    if (existing) {
      req.session.flash = { type: 'error', message: 'Invitation already pending for that email.' };
      return res.redirect(`/workspaces/${wsId}`);
    }

    await pool.query(
      `INSERT INTO workspace_invitations (workspace_id, invited_by, invitee_email, invitee_id) VALUES ($1, $2, $3, $4)`,
      [wsId, userId, invitee_email, invitee ? invitee.user_id : null]
    );
    req.session.flash = { type: 'success', message: `Invitation sent to ${invitee_email}.` };
    res.redirect(`/workspaces/${wsId}`);
  } catch (err) {
    console.error(err);
    req.session.flash = { type: 'error', message: 'Could not send invitation.' };
    res.redirect(`/workspaces/${wsId}`);
  }
});

// ── POST /workspaces/:id/leave
router.post('/:id/leave', requireLogin, async (req, res) => {
  const wsId   = parseInt(req.params.id);
  const userId = req.session.user.user_id;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    // Can't leave if you're the only admin
    const { rows: admins } = await client.query(
      'SELECT user_id FROM workspace_admins WHERE workspace_id=$1', [wsId]
    );
    if (admins.length === 1 && admins[0].user_id === userId) {
      req.session.flash = { type: 'error', message: 'Transfer admin rights before leaving.' };
      await client.query('ROLLBACK');
      return res.redirect(`/workspaces/${wsId}`);
    }
    await client.query('DELETE FROM workspace_members WHERE workspace_id=$1 AND user_id=$2', [wsId, userId]);
    await client.query('DELETE FROM workspace_admins  WHERE workspace_id=$1 AND user_id=$2', [wsId, userId]);
    await client.query('DELETE FROM channel_memberships WHERE user_id=$1 AND channel_id IN (SELECT channel_id FROM channels WHERE workspace_id=$2)', [userId, wsId]);
    await client.query('COMMIT');
    req.session.flash = { type: 'success', message: 'You left the workspace.' };
    res.redirect('/workspaces');
  } catch (err) {
    await client.query('ROLLBACK');
    console.error(err);
    req.session.flash = { type: 'error', message: 'Could not leave workspace.' };
    res.redirect(`/workspaces/${wsId}`);
  } finally {
    client.release();
  }
});

module.exports = router;
