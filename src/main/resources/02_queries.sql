-- ============================================================
-- SNICKR SQL QUERIES  (Project #1, Part c)
-- CS6083, Spring 2026
-- Placeholder values are highlighted with comments.
-- ============================================================


-- ----------------------------------------------------------
-- (1) Create a new user account
--     Placeholders: email, username, nickname, hashed_password
-- ----------------------------------------------------------
WITH new_user AS (
INSERT
INTO users (email, username, password_hash)
VALUES (:email, :username, :hashed_password)
    RETURNING user_id
    )

INSERT
INTO profiles (user_id, nickname)
SELECT user_id, :nickname
FROM new_user;


-- ----------------------------------------------------------
-- (2) Create a new PUBLIC channel inside a workspace
--     by a particular user, with authorization check.
--
--     The check ensures :creator_id is a member of :workspace_id.
--     (Any workspace member may create a channel per the spec.)
--     A sub-select / CTE guards against unauthorized inserts.
-- ----------------------------------------------------------
WITH auth_check AS (SELECT user_id
                    FROM workspace_memberships
                    WHERE workspace_id = :workspace_id
                      AND user_id = :creator_id),
     new_channel AS (
INSERT
INTO channels (workspace_id, name, type, creator_id)
SELECT :workspace_id, :channel_name, 'public', :creator_id
FROM auth_check RETURNING channel_id
)

-- After creating the channel, automatically add the creator as first member:
INSERT
INTO channel_memberships (channel_id, user_id)
SELECT channel_id, :creator_id
FROM new_channel;


-- ----------------------------------------------------------
-- (3) For each workspace, list all current administrators
-- ----------------------------------------------------------
SELECT w.name     AS workspace_name,
       u.username AS admin_username,
       u.email    AS admin_email
FROM workspace_memberships wm
         JOIN workspaces w ON wm.workspace_id = w.workspace_id
         JOIN users u ON wm.user_id = u.user_id
WHERE wm.role = 'administrator'
ORDER BY w.name;


-- ----------------------------------------------------------
-- (4) For each PUBLIC channel in a given workspace,
--     list the number of users invited more than 5 days ago
--     who have NOT yet joined.
--     Placeholder: :workspace_id
-- ----------------------------------------------------------
SELECT c.name               AS channel_name,
       COUNT(ci.invitee_id) AS pending_invites_count
FROM channels c
         JOIN channel_invitations ci ON c.channel_id = ci.channel_id
         LEFT JOIN channel_memberships cm ON ci.channel_id = cm.channel_id
    AND ci.invitee_id = cm.user_id
WHERE c.workspace_id = :workspace_id
  AND c.type = 'public'
  AND ci.created_at < NOW() - INTERVAL '5 days'
  AND cm.user_id IS NULL
GROUP BY c.channel_id, c.name;


-- ----------------------------------------------------------
-- (5) For a particular channel, list all messages
--     in chronological order.
--     Placeholder: :channel_id
-- ----------------------------------------------------------
SELECT m.posted_at,
       u.username AS sender,
       m.body
FROM messages m
         LEFT JOIN users u ON m.sender_id = u.user_id
WHERE m.channel_id = :channel_id
ORDER BY m.posted_at ASC;


-- ----------------------------------------------------------
-- (6) For a particular user, list ALL messages they posted
--     in any channel.
--     Placeholder: :user_id
-- ----------------------------------------------------------
SELECT w.name AS workspace_name,
       c.name AS channel_name,
       m.posted_at,
       m.body
FROM messages m
         JOIN channels c ON m.channel_id = c.channel_id
         JOIN workspaces w ON c.workspace_id = w.workspace_id
WHERE m.sender_id = :user_id
ORDER BY m.posted_at DESC;


-- ----------------------------------------------------------
-- (7) For a particular user, list all ACCESSIBLE messages
--     that contain the keyword "perpendicular".
--
--     "Accessible" = user is a member of the workspace
--                    AND a member of the specific channel.
--     Placeholder: :user_id
-- ----------------------------------------------------------
SELECT m.message_id,
       c.name     AS channel_name,
       w.name     AS workspace_name,
       u.username AS posted_by, -- p.nickname AS posted_by
       m.body,
       m.posted_at
FROM messages m
         JOIN channels c ON m.channel_id = c.channel_id
         JOIN workspaces w ON c.workspace_id = w.workspace_id
         LEFT JOIN users u ON m.sender_id = u.user_id   -- LEFT JOIN profiles p ON m.sender_id = p.user_id
         JOIN workspace_memberships wm ON wm.workspace_id = w.workspace_id
    AND wm.user_id = :user_id
         JOIN channel_memberships cm ON cm.channel_id = c.channel_id
    AND cm.user_id = :user_id
WHERE m.body ILIKE '%perpendicular%'
ORDER BY m.posted_at ASC;