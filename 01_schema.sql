-- ============================================================
-- SNICKR DATABASE SCHEMA
-- CS6083, Spring 2026 | Project #1
-- ============================================================

-- Drop tables in reverse dependency order for clean re-runs
DROP TABLE IF EXISTS channel_invitations;
DROP TABLE IF EXISTS channel_memberships;
DROP TABLE IF EXISTS workspace_invitations;
DROP TABLE IF EXISTS workspace_admins;
DROP TABLE IF EXISTS workspace_members;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS channels;
DROP TABLE IF EXISTS workspaces;
DROP TABLE IF EXISTS users;

-- -------------------------------------------------------
-- USERS
-- -------------------------------------------------------
CREATE TABLE users (
    user_id     SERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    username    VARCHAR(100) NOT NULL UNIQUE,
    nickname    VARCHAR(100),
    password    VARCHAR(255) NOT NULL,   -- store hashed passwords in prod
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -------------------------------------------------------
-- WORKSPACES
-- -------------------------------------------------------
CREATE TABLE workspaces (
    workspace_id  SERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    creator_id    INT NOT NULL REFERENCES users(user_id),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -------------------------------------------------------
-- WORKSPACE MEMBERS  (includes the creator)
-- -------------------------------------------------------
CREATE TABLE workspace_members (
    workspace_id  INT NOT NULL REFERENCES workspaces(workspace_id),
    user_id       INT NOT NULL REFERENCES users(user_id),
    joined_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workspace_id, user_id)
);

-- -------------------------------------------------------
-- WORKSPACE ADMINS
-- -------------------------------------------------------
CREATE TABLE workspace_admins (
    workspace_id  INT NOT NULL REFERENCES workspaces(workspace_id),
    user_id       INT NOT NULL REFERENCES users(user_id),
    granted_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workspace_id, user_id)
);

-- -------------------------------------------------------
-- WORKSPACE INVITATIONS
-- -------------------------------------------------------
CREATE TABLE workspace_invitations (
    invitation_id  SERIAL PRIMARY KEY,
    workspace_id   INT NOT NULL REFERENCES workspaces(workspace_id),
    invited_by     INT NOT NULL REFERENCES users(user_id),  -- must be admin
    invitee_email  VARCHAR(255) NOT NULL,                   -- may not yet be a user
    invitee_id     INT REFERENCES users(user_id),           -- set when accepted
    status         VARCHAR(20) NOT NULL DEFAULT 'pending'
                   CHECK (status IN ('pending','accepted','declined')),
    invited_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at   TIMESTAMP
);

-- -------------------------------------------------------
-- CHANNELS
-- channel_type: 'public' | 'private' | 'direct'
-- For direct channels exactly two members exist in channel_memberships.
-- -------------------------------------------------------
CREATE TABLE channels (
    channel_id    SERIAL PRIMARY KEY,
    workspace_id  INT NOT NULL REFERENCES workspaces(workspace_id),
    name          VARCHAR(255) NOT NULL,
    channel_type  VARCHAR(10) NOT NULL DEFAULT 'public'
                  CHECK (channel_type IN ('public','private','direct')),
    creator_id    INT NOT NULL REFERENCES users(user_id),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (workspace_id, name)          -- channel names unique per workspace
);

-- -------------------------------------------------------
-- CHANNEL MEMBERSHIPS
-- -------------------------------------------------------
CREATE TABLE channel_memberships (
    channel_id  INT NOT NULL REFERENCES channels(channel_id),
    user_id     INT NOT NULL REFERENCES users(user_id),
    joined_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (channel_id, user_id)
);

-- -------------------------------------------------------
-- CHANNEL INVITATIONS  (for private channels)
-- -------------------------------------------------------
CREATE TABLE channel_invitations (
    invitation_id  SERIAL PRIMARY KEY,
    channel_id     INT NOT NULL REFERENCES channels(channel_id),
    invited_by     INT NOT NULL REFERENCES users(user_id),
    invitee_id     INT NOT NULL REFERENCES users(user_id),
    status         VARCHAR(20) NOT NULL DEFAULT 'pending'
                   CHECK (status IN ('pending','accepted','declined')),
    invited_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at   TIMESTAMP
);

-- -------------------------------------------------------
-- MESSAGES
-- -------------------------------------------------------
CREATE TABLE messages (
    message_id   SERIAL PRIMARY KEY,
    channel_id   INT NOT NULL REFERENCES channels(channel_id),
    sender_id    INT NOT NULL REFERENCES users(user_id),
    body         TEXT NOT NULL,
    posted_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Full-text search index on message body (PostgreSQL)
CREATE INDEX idx_messages_body ON messages USING gin(to_tsvector('english', body));
-- Regular indexes for common lookup patterns
CREATE INDEX idx_messages_channel   ON messages(channel_id, posted_at);
CREATE INDEX idx_messages_sender    ON messages(sender_id);
CREATE INDEX idx_ch_inv_channel     ON channel_invitations(channel_id, status, invited_at);
CREATE INDEX idx_ws_members         ON workspace_members(workspace_id, user_id);
