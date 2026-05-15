package com.snickr.repository;

import com.snickr.model.Channel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ChannelRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChannelRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Channel> channelRowMapper = (rs, rowNum) -> {
        Channel channel = new Channel();
        channel.setChannelId(rs.getObject("channel_id", UUID.class));
        channel.setWorkspaceId(rs.getObject("workspace_id", UUID.class));
        channel.setName(rs.getString("name"));
        channel.setType(rs.getString("type"));
        channel.setCreatorId(rs.getObject("creator_id", UUID.class));
        channel.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        return channel;
    };

    /**
     * Create a new channel within the specified workspace
     */
    @Transactional
    public Channel createChannel(Channel channel) {
        String sql = "INSERT INTO channels (workspace_id, name, type, creator_id) " +
                "VALUES (?, ?, ?::channel_type, ?) RETURNING *";

        Channel newChannel = jdbcTemplate.queryForObject(
                sql,
                channelRowMapper,
                channel.getWorkspaceId(),
                channel.getName(),
                channel.getType(),
                channel.getCreatorId()
        );

        if (newChannel != null) {
            String insertMembershipSql = "INSERT INTO channel_memberships (channel_id, user_id) VALUES (?, ?)";
            jdbcTemplate.update(insertMembershipSql, newChannel.getChannelId(), newChannel.getCreatorId());
        }

        return newChannel;
    }

    /**
     * Get all channels within a specified workspace_id
     */
    public List<Channel> findChannelsForUserInWorkspace(UUID workspaceId, UUID userId) {
        String sql = "SELECT DISTINCT c.* FROM channels c " +
                "LEFT JOIN channel_memberships cm ON c.channel_id = cm.channel_id " +
                "WHERE c.workspace_id = ? " +
                "AND (c.type = 'public'::channel_type OR (c.type = 'private'::channel_type AND cm.user_id = ?)) " +
                "ORDER BY c.created_at ASC";
        return jdbcTemplate.query(sql, channelRowMapper, workspaceId, userId);
    }

    public Optional<Channel> findById(UUID channelId) {
        String sql = "SELECT * FROM channels WHERE channel_id = ?";
        List<Channel> channels = jdbcTemplate.query(sql, channelRowMapper, channelId);
        return channels.stream().findFirst();
    }

    /**
     * Find an existing direct channel between two specific users
     */
    public Optional<Channel> findDirectChannelBetweenUsers(UUID workspaceId, UUID user1Id, UUID user2Id) {
        String sql = "SELECT c.* FROM channels c " +
                "JOIN channel_memberships cm1 ON c.channel_id = cm1.channel_id " +
                "JOIN channel_memberships cm2 ON c.channel_id = cm2.channel_id " +
                "WHERE c.workspace_id = ? AND c.type = 'direct'::channel_type " +
                "AND cm1.user_id = ? AND cm2.user_id = ?";
        List<Channel> channels = jdbcTemplate.query(sql, channelRowMapper, workspaceId, user1Id, user2Id);
        return channels.stream().findFirst();
    }

    /**
     * Fetch all direct channels for a user, dynamically aliasing the other user's name as the channel name
     */
    public List<Channel> findDirectChannelsForUser(UUID workspaceId, UUID userId) {
        String sql = "SELECT c.channel_id, c.workspace_id, u.username AS name, c.type, c.creator_id, c.created_at " +
                "FROM channels c " +
                "JOIN channel_memberships cm_me ON c.channel_id = cm_me.channel_id " +
                "JOIN channel_memberships cm_other ON c.channel_id = cm_other.channel_id " +
                "JOIN users u ON cm_other.user_id = u.user_id " +
                "WHERE c.workspace_id = ? AND c.type = 'direct'::channel_type " +
                "AND cm_me.user_id = ? AND cm_other.user_id != ?";
        return jdbcTemplate.query(sql, channelRowMapper, workspaceId, userId, userId);
    }

    /**
     * Get the username of the other participant in a DM
     */
    public String getOtherUsernameInDirectChannel(UUID channelId, UUID myUserId) {
        String sql = "SELECT u.username FROM channel_memberships cm " +
                "JOIN users u ON cm.user_id = u.user_id " +
                "WHERE cm.channel_id = ? AND cm.user_id != ?";
        List<String> names = jdbcTemplate.queryForList(sql, String.class, channelId, myUserId);
        return names.isEmpty() ? "Unknown" : names.get(0);
    }

    /**
     * Create a new direct channel and add both users
     */
    @Transactional
    public Channel createDirectChannel(UUID workspaceId, UUID creatorId, UUID targetId) {
        String insertChannelSql = "INSERT INTO channels (workspace_id, type, creator_id) VALUES (?, 'direct'::channel_type, ?) RETURNING *";
        Channel newChannel = jdbcTemplate.queryForObject(insertChannelSql, channelRowMapper, workspaceId, creatorId);

        if (newChannel != null) {
            String insertMembershipSql = "INSERT INTO channel_memberships (channel_id, user_id) VALUES (?, ?)";
            jdbcTemplate.update(insertMembershipSql, newChannel.getChannelId(), creatorId);
            if (!creatorId.equals(targetId)) {
                jdbcTemplate.update(insertMembershipSql, newChannel.getChannelId(), targetId);
            }
        }
        return newChannel;
    }

    /**
     * New methods for channel invitations
     */
    public boolean isChannelMember(UUID channelId, UUID userId) {
        String sql = "SELECT COUNT(*) FROM channel_memberships WHERE channel_id = ? AND user_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, channelId, userId);
        return count != null && count > 0;
    }

    public boolean hasPendingChannelInvitation(UUID channelId, UUID inviteeId) {
        String sql = "SELECT COUNT(*) FROM channel_invitations WHERE channel_id = ? AND invitee_id = ? AND status = 'pending'::status_type";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, channelId, inviteeId);
        return count != null && count > 0;
    }

    public void createChannelInvitation(UUID channelId, UUID inviterId, UUID inviteeId) {
        String sql = "INSERT INTO channel_invitations (channel_id, inviter_id, invitee_id, status) VALUES (?, ?, ?, 'pending'::status_type)";
        jdbcTemplate.update(sql, channelId, inviterId, inviteeId);
    }

    public List<Map<String, Object>> findPendingChannelInvitationsForUser(UUID workspaceId, UUID userId) {
        String sql = "SELECT ci.invitation_id, c.name AS channel_name, c.channel_id, c.type AS channel_type, u.username AS inviter_name " +
                "FROM channel_invitations ci " +
                "JOIN channels c ON ci.channel_id = c.channel_id " +
                "JOIN users u ON ci.inviter_id = u.user_id " +
                "WHERE ci.invitee_id = ? AND c.workspace_id = ? AND ci.status = 'pending'::status_type " +
                "AND c.type = 'private'::channel_type";
        return jdbcTemplate.queryForList(sql, userId, workspaceId);
    }

    @Transactional
    public void acceptChannelInvitation(UUID invitationId, UUID userId) {
        // Update status and fetch the channel ID
        String updateSql = "UPDATE channel_invitations SET status = 'accepted'::status_type " +
                "WHERE invitation_id = ? AND invitee_id = ? AND status = 'pending'::status_type " +
                "RETURNING channel_id";
        UUID channelId = jdbcTemplate.queryForObject(updateSql, UUID.class, invitationId, userId);

        // Insert into memberships
        if (channelId != null) {
            String insertMembershipSql = "INSERT INTO channel_memberships (channel_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
            jdbcTemplate.update(insertMembershipSql, channelId, userId);
        }
    }
}