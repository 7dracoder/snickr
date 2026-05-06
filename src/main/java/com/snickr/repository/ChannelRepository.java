package com.snickr.repository;

import com.snickr.model.Channel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Responsible for handling database interactions for the 'channels' table.
 */
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
    public Channel createChannel(Channel channel) {
        String sql = "INSERT INTO channels (workspace_id, name, type, creator_id) " +
                "VALUES (?, ?, ?::channel_type, ?) RETURNING *";

        return jdbcTemplate.queryForObject(
                sql,
                channelRowMapper,
                channel.getWorkspaceId(),
                channel.getName(),
                channel.getType(),
                channel.getCreatorId()
        );
    }

    /**
     * Query all channels within a specified workspace_id
     */
    public List<Channel> findChannelsByWorkspaceId(UUID workspaceId) {
        String sql = "SELECT * FROM channels WHERE workspace_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, channelRowMapper, workspaceId);
    }
}