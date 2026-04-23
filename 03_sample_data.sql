-- ============================================================
-- SNICKR SAMPLE DATA  (Project #1, Part d)
-- CS6083, Spring 2026
-- ============================================================
-- Test data overview:
--   5 users: alice, bob, carol, dave, eve
--   2 workspaces: "TechCorp" (alice admin), "BookClub" (carol admin)
--   Channels in TechCorp: #general (public), #engineering (public),
--                          #hiring (private), alice<->bob (direct)
--   Channels in BookClub:  #reads (public), carol<->dave (direct)
--   Messages cover keyword search, chronological order, and
--   the ">5 days uninvited" scenario.
-- ============================================================

-- -------------------------------------------------------
-- USERS  (passwords are fake hashes for demo purposes)
-- -------------------------------------------------------
INSERT INTO users (user_id, email, username, nickname, password, created_at) VALUES
(1, 'alice@snickr.io',  'alice',  'Ali',   'hash_alice',  '2026-03-01 09:00:00'),
(2, 'bob@snickr.io',    'bob',    'Bobby', 'hash_bob',    '2026-03-02 10:00:00'),
(3, 'carol@snickr.io',  'carol',  'Caro',  'hash_carol',  '2026-03-03 11:00:00'),
(4, 'dave@snickr.io',   'dave',   'Davey', 'hash_dave',   '2026-03-04 12:00:00'),
(5, 'eve@snickr.io',    'eve',    'Evie',  'hash_eve',    '2026-03-05 08:00:00');

-- -------------------------------------------------------
-- WORKSPACES
-- -------------------------------------------------------
INSERT INTO workspaces (workspace_id, name, description, creator_id, created_at) VALUES
(1, 'TechCorp',   'Internal workspace for TechCorp employees', 1, '2026-03-05 09:00:00'),
(2, 'BookClub',   'Monthly book discussion group',             3, '2026-03-06 10:00:00');

-- -------------------------------------------------------
-- WORKSPACE MEMBERS
-- -------------------------------------------------------
INSERT INTO workspace_members (workspace_id, user_id, joined_at) VALUES
(1, 1, '2026-03-05 09:00:00'),  -- alice (creator)
(1, 2, '2026-03-05 09:30:00'),  -- bob
(1, 3, '2026-03-05 10:00:00'),  -- carol
(1, 4, '2026-03-06 08:00:00'),  -- dave
(1, 5, '2026-03-06 09:00:00'),  -- eve
(2, 3, '2026-03-06 10:00:00'),  -- carol (creator)
(2, 4, '2026-03-06 11:00:00'),  -- dave
(2, 5, '2026-03-07 08:00:00');  -- eve

-- -------------------------------------------------------
-- WORKSPACE ADMINS
-- -------------------------------------------------------
INSERT INTO workspace_admins (workspace_id, user_id, granted_at) VALUES
(1, 1, '2026-03-05 09:00:00'),  -- alice is admin of TechCorp
(1, 2, '2026-03-05 09:05:00'),  -- bob is co-admin of TechCorp
(2, 3, '2026-03-06 10:00:00');  -- carol is admin of BookClub

-- -------------------------------------------------------
-- WORKSPACE INVITATIONS
-- -------------------------------------------------------
INSERT INTO workspace_invitations
    (workspace_id, invited_by, invitee_email, invitee_id, status, invited_at, responded_at)
VALUES
(1, 1, 'bob@snickr.io',   2, 'accepted', '2026-03-05 09:10:00', '2026-03-05 09:30:00'),
(1, 1, 'carol@snickr.io', 3, 'accepted', '2026-03-05 09:10:00', '2026-03-05 10:00:00'),
(1, 1, 'dave@snickr.io',  4, 'accepted', '2026-03-05 09:15:00', '2026-03-06 08:00:00'),
(1, 1, 'eve@snickr.io',   5, 'accepted', '2026-03-05 09:15:00', '2026-03-06 09:00:00'),
(2, 3, 'dave@snickr.io',  4, 'accepted', '2026-03-06 10:05:00', '2026-03-06 11:00:00'),
(2, 3, 'eve@snickr.io',   5, 'accepted', '2026-03-06 10:05:00', '2026-03-07 08:00:00');

-- -------------------------------------------------------
-- CHANNELS
-- -------------------------------------------------------
INSERT INTO channels (channel_id, workspace_id, name, channel_type, creator_id, created_at) VALUES
(1, 1, 'general',     'public',  1, '2026-03-05 09:05:00'),
(2, 1, 'engineering', 'public',  2, '2026-03-05 09:10:00'),
(3, 1, 'hiring',      'private', 1, '2026-03-05 09:15:00'),
(4, 1, 'alice-bob',   'direct',  1, '2026-03-06 10:00:00'),
(5, 2, 'reads',       'public',  3, '2026-03-06 10:05:00'),
(6, 2, 'carol-dave',  'direct',  3, '2026-03-07 09:00:00');

-- -------------------------------------------------------
-- CHANNEL MEMBERSHIPS
-- -------------------------------------------------------
INSERT INTO channel_memberships (channel_id, user_id, joined_at) VALUES
-- #general (public, TechCorp) – all TechCorp members
(1, 1, '2026-03-05 09:05:00'),
(1, 2, '2026-03-05 09:35:00'),
(1, 3, '2026-03-05 10:05:00'),
(1, 4, '2026-03-06 08:05:00'),
(1, 5, '2026-03-06 09:05:00'),
-- #engineering (public) – alice, bob, dave joined; carol and eve invited but not yet joined
(2, 1, '2026-03-05 09:10:00'),
(2, 2, '2026-03-05 09:40:00'),
(2, 4, '2026-03-06 08:10:00'),
-- #hiring (private) – alice and bob only
(3, 1, '2026-03-05 09:15:00'),
(3, 2, '2026-03-05 09:45:00'),
-- alice<->bob direct
(4, 1, '2026-03-06 10:00:00'),
(4, 2, '2026-03-06 10:01:00'),
-- #reads (public, BookClub) – all BookClub members
(5, 3, '2026-03-06 10:05:00'),
(5, 4, '2026-03-06 11:05:00'),
(5, 5, '2026-03-07 08:05:00'),
-- carol<->dave direct
(6, 3, '2026-03-07 09:00:00'),
(6, 4, '2026-03-07 09:01:00');

-- -------------------------------------------------------
-- CHANNEL INVITATIONS (private + pending > 5 days)
-- carol & eve invited to #engineering on Mar 10 => >5 days old
-- -------------------------------------------------------
INSERT INTO channel_invitations
    (channel_id, invited_by, invitee_id, status, invited_at)
VALUES
(2, 1, 3, 'pending', '2026-03-10 08:00:00'),  -- carol not yet joined #engineering
(2, 1, 5, 'pending', '2026-03-10 08:05:00'),  -- eve  not yet joined #engineering
(3, 1, 2, 'accepted','2026-03-05 09:20:00');   -- bob accepted #hiring invite

-- -------------------------------------------------------
-- MESSAGES
-- -------------------------------------------------------
INSERT INTO messages (message_id, channel_id, sender_id, body, posted_at) VALUES
-- #general
(1,  1, 1, 'Welcome everyone to TechCorp on Snickr!', '2026-03-05 09:06:00'),
(2,  1, 2, 'Thanks Alice, great to be here.', '2026-03-05 09:36:00'),
(3,  1, 3, 'Hello team! Looking forward to collaborating.', '2026-03-05 10:06:00'),
(4,  1, 4, 'Hi all! The new perpendicular axis feature is ready for review.', '2026-03-06 08:06:00'),
(5,  1, 5, 'Exciting! I will check the perpendicular alignment today.', '2026-03-06 09:06:00'),
-- #engineering
(6,  2, 1, 'Lets discuss the new microservices architecture.', '2026-03-05 09:11:00'),
(7,  2, 2, 'I think we should use perpendicular partitioning for the DB shards.', '2026-03-05 09:41:00'),
(8,  2, 4, 'Good idea! I had the same thought about perpendicular indexes.', '2026-03-06 08:11:00'),
-- #hiring (private)
(9,  3, 1, 'We have two strong candidates for the senior role.', '2026-03-05 09:16:00'),
(10, 3, 2, 'Agreed. Lets schedule interviews for next week.', '2026-03-05 09:46:00'),
-- alice<->bob direct
(11, 4, 1, 'Bob, can you review my PR before EOD?', '2026-03-06 10:05:00'),
(12, 4, 2, 'Sure Alice, will do it by 3pm.', '2026-03-06 10:30:00'),
-- #reads (BookClub)
(13, 5, 3, 'This months book is Educated by Tara Westover.', '2026-03-06 10:06:00'),
(14, 5, 4, 'Already started reading, its fascinating!', '2026-03-06 11:06:00'),
(15, 5, 5, 'Chapter 3 mentions a perpendicular valley – great imagery.', '2026-03-07 08:06:00'),
-- carol<->dave direct
(16, 6, 3, 'Dave, what did you think of chapter 5?', '2026-03-07 09:05:00'),
(17, 6, 4, 'Absolutely loved it! Very moving.', '2026-03-07 09:20:00');
