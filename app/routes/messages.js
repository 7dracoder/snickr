// routes/messages.js
const express = require('express');
const { body, validationResult } = require('express-validator');
const pool   = require('../db');
const { requireLogin } = require('../middleware/auth');
const router = express.Router();

// ── POST /messages/api – post a message, return JSON
router.post('/api', requireLogin, async (req, res) => {
  const channelId = parseInt(req.body.channel_id);
  const msgBody   = (req.body.body || '').trim();
  const userId    = req.session.user.user_id;
  if (!msgBody || msgBody.length > 4000) return res.status(400).json({ error: 'Invalid message' });
  try {
    const { rows: [member] } = await pool.query(
      'SELECT 1 FROM channel_memberships WHERE channel_id=$1 AND user_id=$2', [channelId, userId]
    );
    if (!member) return res.status(403).json({ error: 'Not a member' });
    const { rows: [msg] } = await pool.query(
      `INSERT INTO messages (channel_id, sender_id, body) VALUES ($1, $2, $3)
       RETURNING message_id, body, posted_at`,
      [channelId, userId, msgBody]
    );
    const u = req.session.user;
    res.json({ ...msg, user_id: u.user_id, username: u.username, nickname: u.nickname });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Server error' });
  }
});
// ── POST /messages – post a message (form fallback)
router.post('/', requireLogin, [
  body('body').trim().isLength({ min: 1, max: 4000 }).withMessage('Message cannot be empty'),
  body('channel_id').isInt().withMessage('channel_id required'),
], async (req, res) => {
  const errors    = validationResult(req);
  const channelId = parseInt(req.body.channel_id);
  if (!errors.isEmpty()) {
    req.session.flash = { type: 'error', message: errors.array()[0].msg };
    return res.redirect(`/channels/${channelId}`);
  }

  const userId  = req.session.user.user_id;
  const msgBody = req.body.body;
  try {
    const { rows: [member] } = await pool.query(
      'SELECT 1 FROM channel_memberships WHERE channel_id=$1 AND user_id=$2', [channelId, userId]
    );
    if (!member) {
      req.session.flash = { type: 'error', message: 'You are not a member of this channel.' };
      return res.redirect(`/channels/${channelId}`);
    }
    await pool.query(
      'INSERT INTO messages (channel_id, sender_id, body) VALUES ($1, $2, $3)',
      [channelId, userId, msgBody]
    );
    res.redirect(`/channels/${channelId}`);
  } catch (err) {
    console.error(err);
    req.session.flash = { type: 'error', message: 'Could not post message.' };
    res.redirect(`/channels/${channelId}`);
  }
});

// ── POST /messages/:id/delete
router.post('/:id/delete', requireLogin, async (req, res) => {
  const msgId  = parseInt(req.params.id);
  const userId = req.session.user.user_id;
  try {
    const { rows: [msg] } = await pool.query('SELECT * FROM messages WHERE message_id=$1', [msgId]);
    if (!msg) return res.redirect('back');
    if (msg.sender_id !== userId) {
      req.session.flash = { type: 'error', message: 'You can only delete your own messages.' };
      return res.redirect(`/channels/${msg.channel_id}`);
    }
    await pool.query('DELETE FROM messages WHERE message_id=$1', [msgId]);
    res.redirect(`/channels/${msg.channel_id}`);
  } catch (err) {
    console.error(err);
    res.redirect('back');
  }
});

// ── POST /messages/:id/edit
router.post('/:id/edit', requireLogin, [
  body('body').trim().isLength({ min: 1, max: 4000 }).withMessage('Message cannot be empty'),
], async (req, res) => {
  const msgId  = parseInt(req.params.id);
  const userId = req.session.user.user_id;
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    req.session.flash = { type: 'error', message: errors.array()[0].msg };
    return res.redirect('back');
  }
  try {
    const { rows: [msg] } = await pool.query('SELECT * FROM messages WHERE message_id=$1', [msgId]);
    if (!msg) return res.redirect('back');
    if (msg.sender_id !== userId) {
      req.session.flash = { type: 'error', message: 'You can only edit your own messages.' };
      return res.redirect(`/channels/${msg.channel_id}`);
    }
    await pool.query('UPDATE messages SET body=$1 WHERE message_id=$2', [req.body.body, msgId]);
    res.redirect(`/channels/${msg.channel_id}`);
  } catch (err) {
    console.error(err);
    res.redirect('back');
  }
});

module.exports = router;
