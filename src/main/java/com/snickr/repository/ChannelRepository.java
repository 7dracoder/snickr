package com.snickr.repository;

import com.snickr.model.Channel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
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
}