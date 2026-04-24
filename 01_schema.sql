-- ============================================================
-- SNICKR DATABASE SCHEMA
-- CS6083, Spring 2026 | Project #1
-- ============================================================

-- Drop tables in reverse dependency order for clean re-runs
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS channel_memberships;
DROP TABLE IF EXISTS channel_invitations;
DROP TABLE IF EXISTS channels;
DROP TABLE IF EXISTS workspace_memberships;
DROP TABLE IF EXISTS workspace_invitations;
DROP TABLE IF EXISTS workspaces;
DROP TABLE IF EXISTS profiles;
DROP TABLE IF EXISTS users;

DROP TYPE IF EXISTS status_type;
DROP TYPE IF EXISTS role_type;
DROP TYPE IF EXISTS channel_type;


CREATE TYPE channel_type AS ENUM ('public', 'private', 'direct');
CREATE TYPE role_type AS ENUM ('administrator', 'member');
CREATE TYPE status_type AS ENUM ('pending', 'accepted', 'rejected');

-- -------------------------------------------------------
-- USERS
-- -------------------------------------------------------
CREATE TABLE users
(
    user_id       UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    email         TEXT UNIQUE NOT NULL,
    username      TEXT UNIQUE NOT NULL,
    password_hash TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -------------------------------------------------------
-- PROFILES
-- -------------------------------------------------------
CREATE TABLE profiles
(
    user_id    UUID PRIMARY KEY REFERENCES users (user_id) ON DELETE CASCADE,
    nickname      TEXT        NOT NULL,
    bio        TEXT,
    avatar     TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -------------------------------------------------------
-- WORKSPACES
-- -------------------------------------------------------
CREATE TABLE workspaces
(
    workspace_id UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    name         TEXT        NOT NULL,
    description  TEXT,
    creator_id   UUID        NOT NULL REFERENCES users (user_id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -------------------------------------------------------
-- WORKSPACE MEMBERSHIPS
-- role_type: 'administrator' | 'member'
-- -------------------------------------------------------
CREATE TABLE workspace_memberships
(
    workspace_id UUID        NOT NULL REFERENCES workspaces (workspace_id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    role         role_type   NOT NULL DEFAULT 'member',
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (workspace_id, user_id)
);

-- -------------------------------------------------------
-- WORKSPACE INVITATIONS
-- -------------------------------------------------------
CREATE TABLE workspace_invitations
(
    invitation_id UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    workspace_id  UUID        NOT NULL REFERENCES workspaces (workspace_id) ON DELETE CASCADE,
    inviter_id    UUID        NOT NULL REFERENCES users (user_id),
    invitee_email TEXT        NOT NULL,
    status        status_type NOT NULL DEFAULT 'pending',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expiry_at     TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '7 days')
);

-- -------------------------------------------------------
-- CHANNELS
-- channel_type: 'public' | 'private' | 'direct'
-- For direct channels exactly two members exist in channel_memberships.
-- -------------------------------------------------------
CREATE TABLE channels
(
    channel_id   UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    workspace_id UUID         NOT NULL REFERENCES workspaces (workspace_id) ON DELETE CASCADE,
    name         TEXT, -- NULL for direct channel
    type         channel_type NOT NULL,
    creator_id   UUID         NOT NULL REFERENCES users (user_id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_id, name)
);

-- -------------------------------------------------------
-- CHANNEL MEMBERSHIPS
-- -------------------------------------------------------
CREATE TABLE channel_memberships
(
    channel_id UUID        NOT NULL REFERENCES channels (channel_id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (channel_id, user_id)
);

-- -------------------------------------------------------
-- CHANNEL INVITATIONS  (for private channels)
-- -------------------------------------------------------
CREATE TABLE channel_invitations
(
    channel_id UUID        NOT NULL REFERENCES channels (channel_id) ON DELETE CASCADE,
    inviter_id UUID        NOT NULL REFERENCES users (user_id),
    invitee_id UUID        NOT NULL REFERENCES users (user_id),
    status     status_type NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expiry_at  TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '7 days'),
    PRIMARY KEY (channel_id, invitee_id)
);

-- -------------------------------------------------------
-- MESSAGES
-- -------------------------------------------------------
CREATE TABLE messages
(
    message_id UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    channel_id UUID        NOT NULL REFERENCES channels (channel_id) ON DELETE CASCADE,
    sender_id  UUID        REFERENCES users (user_id) ON DELETE SET NULL, -- Allow NULL to keep history message
    body       TEXT        NOT NULL,
    posted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Full-text search index on message body (PostgreSQL)
CREATE INDEX idx_messages_body ON messages USING gin(to_tsvector('english', body));
-- Regular indexes for common lookup patterns
CREATE INDEX idx_messages_channel ON messages (channel_id, posted_at);
CREATE INDEX idx_messages_sender ON messages (sender_id);
CREATE INDEX idx_ch_inv_channel ON channel_invitations (channel_id, status, created_at);
CREATE INDEX idx_ws_members ON workspace_memberships (workspace_id, user_id);
