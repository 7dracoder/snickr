-- ============================================================
-- SNICKR TEST QUERIES  (Project #1, Part d – verified outputs)
-- CS6083, Spring 2026
-- Run AFTER loading 01_schema.sql and 03_sample_data.sql
-- ============================================================

-- ----------------------------------------------------------
-- TEST (1): Create a new user
-- Expected: new row for frank appears in users table
-- ----------------------------------------------------------
INSERT INTO users (user_id, email, username, password_hash)
VALUES ('ffffffff-ffff-ffff-ffff-ffffffffffff', 'frank@snickr.io', 'frank', 'hash_frank');

INSERT INTO profiles (user_id, nickname)
VALUES ('ffffffff-ffff-ffff-ffff-ffffffffffff', 'Frankie');

SELECT u.user_id, u.username, u.email, p.nickname
FROM users u
         JOIN profiles p ON u.user_id = p.user_id
WHERE u.username = 'frank';


-- ----------------------------------------------------------
-- TEST (2): Create a new public channel in TechCorp by alice
-- alice UUID: '00000000-0000-0000-0000-000000000001'
-- TechCorp UUID: '11111111-1111-1111-1111-111111111111'
-- Expected: channel 'updates' created; alice auto-added as member
-- ----------------------------------------------------------
INSERT INTO channels (workspace_id, name, type, creator_id)
SELECT '11111111-1111-1111-1111-111111111111', 'updates', 'public', '00000000-0000-0000-0000-000000000001'
    WHERE EXISTS (
    SELECT 1 FROM workspace_memberships
    WHERE workspace_id = '11111111-1111-1111-1111-111111111111'
      AND user_id = '00000000-0000-0000-0000-000000000001'
);

INSERT INTO channel_memberships (channel_id, user_id)
SELECT channel_id, '00000000-0000-0000-0000-000000000001'
FROM channels
WHERE workspace_id = '11111111-1111-1111-1111-111111111111'
  AND name = 'updates';

SELECT * FROM channels WHERE name = 'updates';
SELECT * FROM channel_memberships
WHERE channel_id = (SELECT channel_id FROM channels WHERE name = 'updates');


-- Unauthorized attempt: user_id=99999999-9999-9999-9999-999999999999 (not a member) should insert 0 rows
INSERT INTO channels (workspace_id, name, type, creator_id)
SELECT '11111111-1111-1111-1111-111111111111', 'unauthorized-channel', 'public', '99999999-9999-9999-9999-999999999999'
    WHERE EXISTS (
    SELECT 1 FROM workspace_memberships
    WHERE workspace_id = '11111111-1111-1111-1111-111111111111'
      AND user_id = '99999999-9999-9999-9999-999999999999'
);
-- Verify: 0 rows inserted, no 'unauthorized-channel' exists
SELECT COUNT(*) AS should_be_zero
FROM channels
WHERE name = 'unauthorized-channel';


-- ----------------------------------------------------------
-- TEST (3): List all workspace administrators
-- Expected:
--   TechCorp -> alice, bob
--   BookClub -> carol
-- ----------------------------------------------------------
SELECT
    w.workspace_id,
    w.name AS workspace_name,
    u.user_id,
    u.username,
    p.nickname,
    wm.joined_at AS admin_since
FROM workspace_memberships wm
         JOIN workspaces w ON w.workspace_id = wm.workspace_id
         JOIN users u      ON u.user_id      = wm.user_id
         JOIN profiles p   ON p.user_id      = u.user_id
WHERE wm.role = 'administrator'
ORDER BY w.name, u.username;


-- ----------------------------------------------------------
-- TEST (4): Public channels in workspace 1... with users invited
--           >5 days ago and still pending (not yet joined)
-- Expected: #engineering with count = 2 (carol, eve)
-- ----------------------------------------------------------
SELECT
    c.channel_id,
    c.name AS channel_name,
    COUNT(ci.invitee_id) AS pending_over_5_days
FROM channels c
         JOIN channel_invitations ci
              ON ci.channel_id = c.channel_id
                  AND ci.status     = 'pending'
                  AND ci.created_at < NOW() - INTERVAL '5 days'
    LEFT JOIN channel_memberships cm
ON cm.channel_id = c.channel_id
    AND cm.user_id    = ci.invitee_id
WHERE c.workspace_id = '11111111-1111-1111-1111-111111111111'
  AND c.type = 'public'
  AND cm.user_id IS NULL
GROUP BY c.channel_id, c.name
ORDER BY c.name;


-- ----------------------------------------------------------
-- TEST (5): All messages in #general (channel_id=c1...) in order
-- Expected: 5 messages from alice, bob, carol, dave, eve
-- ----------------------------------------------------------
SELECT
    m.message_id,
    u.username,
    m.body,
    m.posted_at
FROM messages m
         JOIN users u ON u.user_id = m.sender_id
WHERE m.channel_id = 'c1111111-1111-1111-1111-111111111111'
ORDER BY m.posted_at ASC;


-- ----------------------------------------------------------
-- TEST (6): All messages posted by alice (user_id=01)
-- Expected: messages in #general, #engineering, #hiring, direct
-- ----------------------------------------------------------
SELECT
    m.message_id,
    c.name AS channel_name,
    w.name AS workspace_name,
    m.body,
    m.posted_at
FROM messages m
         JOIN channels c   ON c.channel_id   = m.channel_id
         JOIN workspaces w ON w.workspace_id = c.workspace_id
WHERE m.sender_id = '00000000-0000-0000-0000-000000000001'
ORDER BY m.posted_at ASC;


-- ----------------------------------------------------------
-- TEST (7): Messages containing "perpendicular" accessible to dave (user_id=04)
--   Dave is member of TechCorp and BookClub.
--   Dave is in: #general, #engineering, #reads (but NOT #hiring or direct alice-bob)
--   Expected messages with "perpendicular":
--     msg 4  (#general, dave himself)
--     msg 5  (#general, eve)
--     msg 7  (#engineering, bob)
--     msg 8  (#engineering, dave himself)
--     msg 15 (#reads, eve)
-- ----------------------------------------------------------
SELECT
    m.message_id,
    c.name AS channel_name,
    w.name AS workspace_name,
    u.username AS posted_by,
    m.body,
    m.posted_at
FROM messages m
         JOIN channels c             ON c.channel_id   = m.channel_id
         JOIN workspaces w           ON w.workspace_id = c.workspace_id
         JOIN users u                ON u.user_id      = m.sender_id
         JOIN workspace_memberships wm ON wm.workspace_id = w.workspace_id
    AND wm.user_id      = '00000000-0000-0000-0000-000000000004'
         JOIN channel_memberships cm   ON cm.channel_id   = c.channel_id
    AND cm.user_id      = '00000000-0000-0000-0000-000000000004'
WHERE to_tsvector('english', m.body) @@ to_tsquery('english', 'perpendicular')
ORDER BY m.posted_at ASC;
