-- ============================================================
-- SNICKR TEST QUERIES  (Project #1, Part d – verified outputs)
-- CS6083, Spring 2026
-- Run AFTER loading 01_schema.sql and 03_sample_data.sql
-- ============================================================

-- ----------------------------------------------------------
-- TEST (1): Create a new user
-- Expected: new row for frank appears in users table
-- ----------------------------------------------------------
INSERT INTO users (email, username, nickname, password)
VALUES ('frank@snickr.io', 'frank', 'Frankie', 'hash_frank');

SELECT * FROM users WHERE username = 'frank';


-- ----------------------------------------------------------
-- TEST (2): Create a new public channel in workspace 1 by alice (user_id=1)
-- Expected: channel 'updates' created; alice auto-added as member
-- ----------------------------------------------------------
INSERT INTO channels (workspace_id, name, channel_type, creator_id)
SELECT 1, 'updates', 'public', 1
WHERE EXISTS (
    SELECT 1 FROM workspace_members
    WHERE workspace_id = 1 AND user_id = 1
);

INSERT INTO channel_memberships (channel_id, user_id)
SELECT channel_id, 1
FROM channels
WHERE workspace_id = 1 AND name = 'updates';

SELECT * FROM channels WHERE name = 'updates';


-- Unauthorized attempt: user_id=99 (not a member) should insert 0 rows
INSERT INTO channels (workspace_id, name, channel_type, creator_id)
SELECT 1, 'unauthorized-channel', 'public', 99
WHERE EXISTS (
    SELECT 1 FROM workspace_members
    WHERE workspace_id = 1 AND user_id = 99
);
-- Verify: 0 rows inserted, no 'unauthorized-channel' exists
SELECT COUNT(*) AS should_be_zero FROM channels WHERE name = 'unauthorized-channel';


-- ----------------------------------------------------------
-- TEST (3): List all workspace administrators
-- Expected: TechCorp -> alice, bob;  BookClub -> carol
-- ----------------------------------------------------------
SELECT
    w.workspace_id, w.name AS workspace_name,
    u.user_id, u.username, u.nickname, wa.granted_at
FROM workspace_admins wa
JOIN workspaces w ON w.workspace_id = wa.workspace_id
JOIN users      u ON u.user_id      = wa.user_id
ORDER BY w.workspace_id, u.username;


-- ----------------------------------------------------------
-- TEST (4): Public channels in workspace 1 with users invited
--           >5 days ago and still pending (not yet joined)
-- Expected: #engineering with count = 2 (carol, eve)
-- ----------------------------------------------------------
SELECT
    c.channel_id, c.name AS channel_name,
    COUNT(ci.invitee_id) AS pending_over_5_days
FROM channels c
JOIN channel_invitations ci
    ON ci.channel_id = c.channel_id
   AND ci.status     = 'pending'
   AND ci.invited_at < CURRENT_TIMESTAMP - INTERVAL '5 days'
LEFT JOIN channel_memberships cm
    ON cm.channel_id = c.channel_id
   AND cm.user_id    = ci.invitee_id
WHERE c.workspace_id = 1
  AND c.channel_type = 'public'
  AND cm.user_id IS NULL
GROUP BY c.channel_id, c.name
ORDER BY c.name;


-- ----------------------------------------------------------
-- TEST (5): All messages in #general (channel_id=1) in order
-- Expected: 5 messages from alice, bob, carol, dave, eve
-- ----------------------------------------------------------
SELECT m.message_id, u.username, m.body, m.posted_at
FROM messages m
JOIN users u ON u.user_id = m.sender_id
WHERE m.channel_id = 1
ORDER BY m.posted_at ASC;


-- ----------------------------------------------------------
-- TEST (6): All messages posted by alice (user_id=1)
-- Expected: messages 1, 6, 9, 11 (in #general, #engineering, #hiring, direct)
-- ----------------------------------------------------------
SELECT m.message_id, c.name AS channel_name, w.name AS workspace_name,
       m.body, m.posted_at
FROM messages m
JOIN channels   c ON c.channel_id   = m.channel_id
JOIN workspaces w ON w.workspace_id = c.workspace_id
WHERE m.sender_id = 1
ORDER BY m.posted_at ASC;


-- ----------------------------------------------------------
-- TEST (7): Messages containing "perpendicular" accessible to dave (user_id=4)
--   Dave is member of TechCorp and BookClub.
--   Dave is in: #general, #engineering, #reads (but NOT #hiring or direct alice-bob)
--   Expected messages with "perpendicular":
--     msg 4  (#general, dave himself)
--     msg 5  (#general, eve)
--     msg 7  (#engineering, bob)
--     msg 8  (#engineering, dave himself)
--     msg 15 (#reads, eve)
-- ----------------------------------------------------------
SELECT m.message_id, c.name AS channel_name, w.name AS workspace_name,
       u.username AS posted_by, m.body, m.posted_at
FROM messages m
JOIN channels          c  ON c.channel_id   = m.channel_id
JOIN workspaces        w  ON w.workspace_id = c.workspace_id
JOIN users             u  ON u.user_id      = m.sender_id
JOIN workspace_members wm ON wm.workspace_id = w.workspace_id
                          AND wm.user_id    = 4
JOIN channel_memberships cm ON cm.channel_id = c.channel_id
                            AND cm.user_id  = 4
WHERE to_tsvector('english', m.body) @@ to_tsquery('english', 'perpendicular')
ORDER BY m.posted_at ASC;
