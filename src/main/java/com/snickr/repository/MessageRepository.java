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
     * Insert new message
     */
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
}