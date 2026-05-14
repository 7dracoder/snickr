// routes/invitations.js – respond to workspace & channel invitations
const express = require('express');
const pool    = require('../db');
const { requireLogin } = require('../middleware/auth');
const router  = express.Router();

// ── GET /invitations – list pending invitations ───────────────
router.get('/', requireLogin, async (req, res) => {
  const userId = req.session.user.user_id;
  const email  = req.session.user.email;
  try {
    // Workspace invitations
    const { rows: wsInvites } = await pool.query(
      `SELECT wi.invitation_id, wi.workspace_id, wi.invited_at,
              w.name AS workspace_name,
              u.username AS invited_by_name
       FROM workspace_invitations wi
       JOIN workspaces w ON w.workspace_id = wi.workspace_id
       JOIN users u ON u.user_id = wi.invited_by
       WHERE wi.invitee_email = $1 AND wi.status = 'pending'
       ORDER BY wi.invited_at DESC`,
      [email]
    );

    // Channel invitations
    const { rows: chInvites } = await pool.query(
      `SELECT ci.invitation_id, ci.channel_id, ci.invited_at,
              c.name AS channel_name, c.channel_type,
              w.name AS workspace_name, w.workspace_id,
              u.username AS invited_by_name
       FROM channel_invitations ci
       JOIN channels c ON c.channel_id = ci.channel_id
       JOIN workspaces w ON w.workspace_id = c.workspace_id
       JOIN users u ON u.user_id = ci.invited_by
       WHERE ci.invitee_id = $1 AND ci.status = 'pending'
       ORDER BY ci.invited_at DESC`,
      [userId]
    );

    res.render('invitations/index', { wsInvites, chInvites });
  } catch (err) {
    console.error(err);
    res.render('error', { message: 'Could not load invitations', code: 500 });
  }
});

// ── POST /invitations/workspace/:id/respond ───────────────────
router.post('/workspace/:id/respond', requireLogin, async (req, res) => {
  const invId  = parseInt(req.params.id);
  const userId = req.session.user.user_id;
  const email  = req.session.user.email;
  const action = req.body.action; // 'accepted' | 'declined'

  if (!['accepted', 'declined'].includes(action)) return res.redirect('/invitations');

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const { rows: [inv] } = await client.query(
      `SELECT * FROM workspace_invitations
       WHERE invitation_id=$1 AND invitee_email=$2 AND status='pending'`,
      [invId, email]
    );
    if (!inv) {
      req.session.flash = { type: 'error', message: 'Invitation not found.' };
      await client.query('ROLLBACK');
      return res.redirect('/invitations');
    }

    await client.query(
      `UPDATE workspace_invitations
       SET status=$1, invitee_id=$2, responded_at=NOW()
       WHERE invitation_id=$3`,
      [action, userId, invId]
    );

    if (action === 'accepted') {
      await client.query(
        `INSERT INTO workspace_members (workspace_id, user_id)
         VALUES ($1, $2) ON CONFLICT DO NOTHING`,
        [inv.workspace_id, userId]
      );
    }
    await client.query('COMMIT');
    req.session.flash = { type: 'success', message: `Invitation ${action}.` };
    res.redirect(action === 'accepted' ? `/workspaces/${inv.workspace_id}` : '/invitations');
  } catch (err) {
    await client.query('ROLLBACK');
    console.error(err);
    req.session.flash = { type: 'error', message: 'Could not respond to invitation.' };
    res.redirect('/invitations');
  } finally {
    client.release();
  }
});

// ── POST /invitations/channel/:id/respond ─────────────────────
router.post('/channel/:id/respond', requireLogin, async (req, res) => {
  const invId  = parseInt(req.params.id);
  const userId = req.session.user.user_id;
  const action = req.body.action;

  if (!['accepted', 'declined'].includes(action)) return res.redirect('/invitations');

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const { rows: [inv] } = await client.query(
      `SELECT * FROM channel_invitations
       WHERE invitation_id=$1 AND invitee_id=$2 AND status='pending'`,
      [invId, userId]
    );
    if (!inv) {
      req.session.flash = { type: 'error', message: 'Invitation not found.' };
      await client.query('ROLLBACK');
      return res.redirect('/invitations');
    }

    await client.query(
      `UPDATE channel_invitations
       SET status=$1, responded_at=NOW()
       WHERE invitation_id=$2`,
      [action, invId]
    );

    if (action === 'accepted') {
      await client.query(
        `INSERT INTO channel_memberships (channel_id, user_id)
         VALUES ($1, $2) ON CONFLICT DO NOTHING`,
        [inv.channel_id, userId]
      );
    }
    await client.query('COMMIT');
    req.session.flash = { type: 'success', message: `Invitation ${action}.` };
    res.redirect(action === 'accepted' ? `/channels/${inv.channel_id}` : '/invitations');
  } catch (err) {
    await client.query('ROLLBACK');
    console.error(err);
    req.session.flash = { type: 'error', message: 'Could not respond to invitation.' };
    res.redirect('/invitations');
  } finally {
    client.release();
  }
});

module.exports = router;
