# Snickr – Relational Database Design
### CS6083, Spring 2026 | Project #1
**Due:** April 27, 2026

---

## 1. Introduction

Snickr is a web-based team collaboration system modelled after Slack. Users sign up, create workspaces, invite colleagues, build channels within those workspaces, and exchange messages. This report covers the complete relational database design for Snickr, including:

- Entity-Relationship (ER) diagram
- Relational schema with keys and foreign key constraints
- SQL DDL (schema creation)
- Seven required SQL queries
- Sample data and test results
- Design assumptions and justifications

---

## 2. Requirements Summary

| Entity | Key Attributes |
|---|---|
| User | email, username, nickname, password |
| Workspace | name, description, creator |
| Channel | name, type (public/private/direct), workspace, creator |
| Message | body, timestamp, channel, sender |
| Workspace Membership | user ↔ workspace join record |
| Workspace Admin | subset of members with elevated privileges |
| Workspace Invitation | admin invites email to workspace |
| Channel Membership | user ↔ channel join record |
| Channel Invitation | creator invites workspace member to private channel |

---

## 3. Assumptions

1. **Any workspace member can create a channel** – the spec states "Any user in a workspace can create channels." Authorization in query (2) checks workspace membership, not admin status.
2. **Direct channels are workspace-scoped** – two users create a direct channel within a specific workspace; the channel appears in the channels table with `channel_type = 'direct'`.
3. **Passwords are stored hashed** – the schema stores VARCHAR(255) which is large enough for bcrypt/SHA-256 digests; the application layer handles hashing.
4. **Invitee email in workspace invitations may not yet be a registered user** – `invitee_id` is nullable, set when the invitee creates an account and accepts.
5. **No message threading or deletion** – per spec, not required for Part 1.
6. **No payment/premium logic** – all features are free per spec.
7. **Timestamps stored for all actions** – creation and response timestamps on every relevant table.
8. **Channel names are unique per workspace** – enforced by UNIQUE(workspace_id, name).

---

## 4. ER Diagram (Entities and Relationships)

### Entities

- **User** (user_id PK, email, username, nickname, password, created_at)
- **Workspace** (workspace_id PK, name, description, creator_id FK→User, created_at)
- **Channel** (channel_id PK, workspace_id FK→Workspace, name, channel_type, creator_id FK→User, created_at)
- **Message** (message_id PK, channel_id FK→Channel, sender_id FK→User, body, posted_at)

### Relationship / Associative Tables

| Table | Relationship |
|---|---|
| workspace_members | User **belongs to** Workspace (M:N) |
| workspace_admins | User **administers** Workspace (M:N subset of members) |
| workspace_invitations | Admin **invites** email to Workspace |
| channel_memberships | User **joins** Channel (M:N) |
| channel_invitations | User **invites** another User to Channel |

---

## 5. Relational Schema

```sql
users(user_id, email*, username*, nickname, password, created_at)
  PK: user_id
  UNIQUE: email, username

workspaces(workspace_id, name, description, creator_id, created_at)
  PK: workspace_id
  FK: creator_id → users(user_id)

workspace_members(workspace_id, user_id, joined_at)
  PK: (workspace_id, user_id)
  FK: workspace_id → workspaces, user_id → users

workspace_admins(workspace_id, user_id, granted_at)
  PK: (workspace_id, user_id)
  FK: workspace_id → workspaces, user_id → users

workspace_invitations(invitation_id, workspace_id, invited_by,
                       invitee_email, invitee_id, status, invited_at, responded_at)
  PK: invitation_id
  FK: workspace_id → workspaces, invited_by → users, invitee_id → users (nullable)

channels(channel_id, workspace_id, name, channel_type, creator_id, created_at)
  PK: channel_id
  UNIQUE: (workspace_id, name)
  FK: workspace_id → workspaces, creator_id → users
  CHECK: channel_type IN ('public','private','direct')

channel_memberships(channel_id, user_id, joined_at)
  PK: (channel_id, user_id)
  FK: channel_id → channels, user_id → users

channel_invitations(invitation_id, channel_id, invited_by, invitee_id,
                     status, invited_at, responded_at)
  PK: invitation_id
  FK: channel_id → channels, invited_by → users, invitee_id → users
  CHECK: status IN ('pending','accepted','declined')

messages(message_id, channel_id, sender_id, body, posted_at)
  PK: message_id
  FK: channel_id → channels, sender_id → users
```

---

## 6. SQL Queries (Part c)

### Query 1 – Create a New User
```sql
INSERT INTO users (email, username, nickname, password)
VALUES ('alice@example.com', 'alice42', 'Ali', 'hashed_password_here');
```

### Query 2 – Create a Public Channel (with authorization check)
```sql
INSERT INTO channels (workspace_id, name, channel_type, creator_id)
SELECT :workspace_id, :channel_name, 'public', :creator_id
WHERE EXISTS (
    SELECT 1 FROM workspace_members
    WHERE workspace_id = :workspace_id AND user_id = :creator_id
);

INSERT INTO channel_memberships (channel_id, user_id)
VALUES (currval('channels_channel_id_seq'), :creator_id);
```
The authorization check (`WHERE EXISTS`) ensures only workspace members can create channels.

### Query 3 – List All Workspace Administrators
```sql
SELECT w.workspace_id, w.name AS workspace_name,
       u.user_id, u.username, u.nickname, wa.granted_at
FROM workspace_admins wa
JOIN workspaces w ON w.workspace_id = wa.workspace_id
JOIN users      u ON u.user_id      = wa.user_id
ORDER BY w.workspace_id, u.username;
```

### Query 4 – Pending Channel Invitations Older Than 5 Days (per public channel)
```sql
SELECT c.channel_id, c.name AS channel_name,
       COUNT(ci.invitee_id) AS pending_over_5_days
FROM channels c
JOIN channel_invitations ci
    ON ci.channel_id = c.channel_id
   AND ci.status     = 'pending'
   AND ci.invited_at < CURRENT_TIMESTAMP - INTERVAL '5 days'
LEFT JOIN channel_memberships cm
    ON cm.channel_id = c.channel_id AND cm.user_id = ci.invitee_id
WHERE c.workspace_id = :workspace_id
  AND c.channel_type = 'public'
  AND cm.user_id IS NULL
GROUP BY c.channel_id, c.name
ORDER BY c.name;
```
The LEFT JOIN + NULL check confirms the invited user genuinely has not yet joined.

### Query 5 – All Messages in a Channel (Chronological)
```sql
SELECT m.message_id, u.username, m.body, m.posted_at
FROM messages m
JOIN users u ON u.user_id = m.sender_id
WHERE m.channel_id = :channel_id
ORDER BY m.posted_at ASC;
```

### Query 6 – All Messages Posted by a User
```sql
SELECT m.message_id, c.name AS channel_name, w.name AS workspace_name,
       m.body, m.posted_at
FROM messages m
JOIN channels   c ON c.channel_id   = m.channel_id
JOIN workspaces w ON w.workspace_id = c.workspace_id
WHERE m.sender_id = :user_id
ORDER BY m.posted_at ASC;
```

### Query 7 – Accessible Messages Containing "perpendicular"
```sql
SELECT m.message_id, c.name AS channel_name, w.name AS workspace_name,
       u.username AS posted_by, m.body, m.posted_at
FROM messages m
JOIN channels          c  ON c.channel_id   = m.channel_id
JOIN workspaces        w  ON w.workspace_id = c.workspace_id
JOIN users             u  ON u.user_id      = m.sender_id
JOIN workspace_members wm ON wm.workspace_id = w.workspace_id
                          AND wm.user_id    = :user_id
JOIN channel_memberships cm ON cm.channel_id = c.channel_id
                            AND cm.user_id  = :user_id
WHERE to_tsvector('english', m.body) @@ to_tsquery('english', 'perpendicular')
ORDER BY m.posted_at ASC;
```
Using PostgreSQL's GIN full-text index ensures this query scales even with millions of messages.

---

## 7. Sample Test Data (Part d)

### Users
| user_id | username | nickname | email |
|---|---|---|---|
| 1 | alice  | Ali   | alice@snickr.io |
| 2 | bob    | Bobby | bob@snickr.io   |
| 3 | carol  | Caro  | carol@snickr.io |
| 4 | dave   | Davey | dave@snickr.io  |
| 5 | eve    | Evie  | eve@snickr.io   |

### Workspaces
| workspace_id | name | Creator/Admin |
|---|---|---|
| 1 | TechCorp | alice (admin), bob (co-admin) |
| 2 | BookClub | carol (admin) |

### Channels
| channel_id | workspace | name | type | members |
|---|---|---|---|---|
| 1 | TechCorp | general     | public  | alice, bob, carol, dave, eve |
| 2 | TechCorp | engineering | public  | alice, bob, dave (carol & eve invited but pending) |
| 3 | TechCorp | hiring      | private | alice, bob |
| 4 | TechCorp | alice-bob   | direct  | alice, bob |
| 5 | BookClub | reads       | public  | carol, dave, eve |
| 6 | BookClub | carol-dave  | direct  | carol, dave |

### Test Cases Covered

| Test | Expected Result |
|---|---|
| Query 1 – insert frank | frank appears in users |
| Query 2 – alice creates #updates | channel inserted; 0 rows for unauthorized user |
| Query 3 – list admins | TechCorp: alice, bob; BookClub: carol |
| Query 4 – pending > 5 days in ws 1 | engineering: 2 (carol, eve) |
| Query 5 – msgs in #general | 5 msgs in chronological order |
| Query 6 – alice's msgs | msgs 1, 6, 9, 11 across channels |
| Query 7 – "perpendicular" for dave | msgs 4, 5 (#general), 7, 8 (#engineering), 15 (#reads) |

### Why This Data Is Interesting
- Carol and eve are invited to #engineering but have NOT joined → tests query 4
- The word "perpendicular" appears in multiple channels, some of which dave can access and some (like #hiring) he cannot → tests query 7's access control
- Bob is both a workspace member and a co-admin → tests admin/member separation
- Direct channels ensure type filtering logic works correctly

---

## 8. Design Justifications

### Space Efficiency
- `workspace_members` and `channel_memberships` use composite PKs, avoiding surrogate keys where unnecessary.
- `invitee_email` is kept only in `workspace_invitations` (where the user may not exist yet); all other foreign keys use integer IDs.
- `body TEXT` in messages allows variable-length storage without over-allocating.

### Normalization
The schema is in **3NF**. Each non-key attribute depends solely on its primary key. Roles (admin, member) are stored as separate tables rather than as flags in the user table, avoiding partial dependencies and update anomalies.

### Access Control Strategy
Per the project spec, access control is handled at the **application layer**, not via DBMS views or roles. The database can see all content; the application uses session cookies to identify the logged-in user and performs join-based filtering (as in query 7) to restrict what each user sees.

### Indexing
- `GIN` index on `messages.body` for full-text keyword search (query 7)
- Composite index on `messages(channel_id, posted_at)` for chronological message retrieval (query 5)
- Index on `channel_invitations(channel_id, status, invited_at)` for efficient query 4

---

## 9. Files Submitted

| File | Description |
|---|---|
| `01_schema.sql` | Full DDL: CREATE TABLE statements with all constraints |
| `02_queries.sql` | The 7 SQL queries (c1–c7) with placeholder values |
| `03_sample_data.sql` | INSERT statements populating test data |
| `04_test_queries.sql` | Test versions of queries with expected output comments |
| `er_diagram.mmd` | Mermaid ER diagram source |
| `project_report.md` | This document |
