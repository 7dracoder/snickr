package com.snickr.repository;

import com.snickr.model.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Responsible for handling database interactions for the 'Messages' table
 */
@Repository
public class MessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Message> messageRowMapper = (rs, rowNum) -> {
        Message message = new Message();
        message.setMessageId(rs.getObject("message_id", UUID.class));
        message.setChannelId(rs.getObject("channel_id", UUID.class));
        message.setSenderId(rs.getObject("sender_id", UUID.class));
        message.setBody(rs.getString("body"));
        message.setPostedAt(rs.getObject("posted_at", OffsetDateTime.class));

        message.setSenderName(rs.getString("sender_name"));
        return message;
    };

    /**
     * RowMapper specifically for search results, as it includes channel_name
     */
    private final RowMapper<Message> searchResultRowMapper = (rs, rowNum) -> {
        Message message = messageRowMapper.mapRow(rs, rowNum);
        if (message != null) {
            message.setChannelName(rs.getString("channel_name"));
        }
        return message;
    };

    public void createMessage(UUID channelId, UUID senderId, String body) {
        String sql = "INSERT INTO messages (channel_id, sender_id, body) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, channelId, senderId, body);
    }

    /**
     * Retrieve all messages within a specified channel (chronological order)
     */
    public List<Message> findMessagesByChannelId(UUID channelId) {
        String sql = "SELECT m.*, u.username as sender_name FROM messages m " +
                "LEFT JOIN users u ON m.sender_id = u.user_id " +
                "WHERE m.channel_id = ? " +
                "ORDER BY m.posted_at ASC";
        return jdbcTemplate.query(sql, messageRowMapper, channelId);
    }

    /**
     * Performs a global search across all authorized channels in a workspace
     */
    public List<Message> searchMessages(UUID workspaceId, UUID userId, String keyword) {
        String sql = "SELECT m.*, u.username as sender_name, c.name as channel_name " +
                "FROM messages m " +
                "JOIN users u ON m.sender_id = u.user_id " +
                "JOIN channels c ON m.channel_id = c.channel_id " +
                "WHERE c.workspace_id = ? " +
                "AND (c.type = 'public'::channel_type OR (c.type = 'private'::channel_type AND EXISTS (" +
                "    SELECT 1 FROM channel_memberships cm WHERE cm.channel_id = c.channel_id AND cm.user_id = ?" +
                "))) " +
                "AND m.body ILIKE ? " +
                "ORDER BY m.posted_at DESC";

        String searchPattern = "%" + keyword + "%";
        return jdbcTemplate.query(sql, searchResultRowMapper, workspaceId, userId, searchPattern);
    }
}