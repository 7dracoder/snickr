-- ============================================================
-- SNICKR SQL QUERIES  (Project #1, Part c)
-- CS6083, Spring 2026
-- Placeholder values are highlighted with comments.
-- ============================================================


-- ----------------------------------------------------------
-- (1) Create a new user account
--     Placeholders: email, username, nickname, hashed_password
-- ----------------------------------------------------------
INSERT INTO users (email, username, nickname, password)
VALUES (
    'alice@example.com',      -- :email
    'alice42',                -- :username
    'Ali',                    -- :nickname
    'hashed_password_here'    -- :password  (always store hashed)
);


-- ----------------------------------------------------------
-- (2) Create a new PUBLIC channel inside a workspace
--     by a particular user, with authorization check.
--
--     The check ensures :creator_id is a member of :workspace_id.
--     (Any workspace member may create a channel per the spec.)
--     A sub-select / CTE guards against unauthorized inserts.
-- ----------------------------------------------------------
INSERT INTO channels (workspace_id, name, channel_type, creator_id)
SELECT
    :workspace_id,
    :channel_name,          -- e.g. 'announcements'
    'public',
    :creator_id
WHERE EXISTS (
    SELECT 1
    FROM workspace_members
    WHERE workspace_id = :workspace_id
      AND user_id      = :creator_id
);

-- After creating the channel, automatically add the creator as first member:
INSERT INTO channel_memberships (channel_id, user_id)
VALUES (currval('channels_channel_id_seq'), :creator_id);


-- ----------------------------------------------------------
-- (3) For each workspace, list all current administrators
-- ----------------------------------------------------------
SELECT
    w.workspace_id,
    w.name          AS workspace_name,
    u.user_id,
    u.username,
    u.nickname,
    wa.granted_at
FROM workspace_admins wa
JOIN workspaces w ON w.workspace_id = wa.workspace_id
JOIN users      u ON u.user_id      = wa.user_id
ORDER BY w.workspace_id, u.username;


-- ----------------------------------------------------------
-- (4) For each PUBLIC channel in a given workspace,
--     list the number of users invited more than 5 days ago
--     who have NOT yet joined.
--     Placeholder: :workspace_id
-- ----------------------------------------------------------
SELECT
    c.channel_id,
    c.name          AS channel_name,
    COUNT(ci.invitee_id) AS pending_over_5_days
FROM channels c
JOIN channel_invitations ci
    ON ci.channel_id = c.channel_id
   AND ci.status     = 'pending'
   AND ci.invited_at < CURRENT_TIMESTAMP - INTERVAL '5 days'
-- "not yet joined" = no row in channel_memberships
LEFT JOIN channel_memberships cm
    ON cm.channel_id = c.channel_id
   AND cm.user_id    = ci.invitee_id
WHERE c.workspace_id  = :workspace_id
  AND c.channel_type  = 'public'
  AND cm.user_id IS NULL               -- confirms they have not joined
GROUP BY c.channel_id, c.name
ORDER BY c.name;


-- ----------------------------------------------------------
-- (5) For a particular channel, list all messages
--     in chronological order.
--     Placeholder: :channel_id
-- ----------------------------------------------------------
SELECT
    m.message_id,
    u.username,
    u.nickname,
    m.body,
    m.posted_at
FROM messages m
JOIN users u ON u.user_id = m.sender_id
WHERE m.channel_id = :channel_id
ORDER BY m.posted_at ASC;


-- ----------------------------------------------------------
-- (6) For a particular user, list ALL messages they posted
--     in any channel.
--     Placeholder: :user_id
-- ----------------------------------------------------------
SELECT
    m.message_id,
    c.name      AS channel_name,
    w.name      AS workspace_name,
    m.body,
    m.posted_at
FROM messages m
JOIN channels   c ON c.channel_id   = m.channel_id
JOIN workspaces w ON w.workspace_id = c.workspace_id
WHERE m.sender_id = :user_id
ORDER BY m.posted_at ASC;


-- ----------------------------------------------------------
-- (7) For a particular user, list all ACCESSIBLE messages
--     that contain the keyword "perpendicular".
--
--     "Accessible" = user is a member of the workspace
--                    AND a member of the specific channel.
--     Placeholder: :user_id
-- ----------------------------------------------------------
SELECT
    m.message_id,
    c.name      AS channel_name,
    w.name      AS workspace_name,
    u.username  AS posted_by,
    m.body,
    m.posted_at
FROM messages m
JOIN channels          c  ON c.channel_id   = m.channel_id
JOIN workspaces        w  ON w.workspace_id = c.workspace_id
JOIN users             u  ON u.user_id      = m.sender_id
-- user must be a workspace member
JOIN workspace_members wm ON wm.workspace_id = w.workspace_id
                          AND wm.user_id     = :user_id
-- user must be a channel member
JOIN channel_memberships cm ON cm.channel_id = c.channel_id
                            AND cm.user_id   = :user_id
WHERE to_tsvector('english', m.body) @@ to_tsquery('english', 'perpendicular')
   -- fallback LIKE for non-PostgreSQL engines:
   -- OR m.body ILIKE '%perpendicular%'
ORDER BY m.posted_at ASC;
