// routes/search.js – full-text search across accessible messages
const express = require('express');
const pool    = require('../db');
const { requireLogin } = require('../middleware/auth');
const router  = express.Router();

// ── GET /search?q=keyword&workspace_id=N ─────────────────────
router.get('/', requireLogin, async (req, res) => {
  const userId = req.session.user.user_id;
  const q      = (req.query.q || '').trim();
  const wsId   = req.query.workspace_id ? parseInt(req.query.workspace_id) : null;

  // Fetch workspaces for filter dropdown
  const { rows: workspaces } = await pool.query(
    `SELECT w.workspace_id, w.name
     FROM workspaces w
     JOIN workspace_members wm ON wm.workspace_id = w.workspace_id
     WHERE wm.user_id = $1 ORDER BY w.name`,
    [userId]
  );

  if (!q) return res.render('search/index', { results: [], q: '', workspaces, wsId });

  try {
    // Sanitize query for tsquery – replace non-word chars, join with &
    const tsQuery = q.replace(/[^a-zA-Z0-9\s]/g, ' ')
                     .trim()
                     .split(/\s+/)
                     .filter(Boolean)
                     .join(' & ');

    const params = [userId, tsQuery, `%${q}%`];
    let wsFilter = '';
    if (wsId) {
      wsFilter = 'AND w.workspace_id = $4';
      params.push(wsId);
    }

    const { rows: results } = await pool.query(
      `SELECT m.message_id, m.body, m.posted_at,
              c.channel_id, c.name AS channel_name, c.channel_type,
              w.workspace_id, w.name AS workspace_name,
              u.username AS sender_username, u.nickname AS sender_nickname
       FROM messages m
       JOIN channels          c  ON c.channel_id   = m.channel_id
       JOIN workspaces        w  ON w.workspace_id = c.workspace_id
       JOIN users             u  ON u.user_id      = m.sender_id
       JOIN workspace_members wm ON wm.workspace_id = w.workspace_id AND wm.user_id = $1
       JOIN channel_memberships cm ON cm.channel_id = c.channel_id AND cm.user_id = $1
       WHERE (
         to_tsvector('english', m.body) @@ to_tsquery('english', $2)
         OR m.body ILIKE $3
       )
       ${wsFilter}
       ORDER BY m.posted_at DESC
       LIMIT 100`,
      params
    );

    res.render('search/index', { results, q, workspaces, wsId });
  } catch (err) {
    console.error(err);
    res.render('search/index', { results: [], q, workspaces, wsId, error: 'Search failed.' });
  }
});

module.exports = router;
