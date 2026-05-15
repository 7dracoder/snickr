package com.snickr.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Message table
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private UUID messageId;
    private UUID channelId;
    private UUID senderId;
    private String body;
    private OffsetDateTime postedAt;

    private String senderName;
    private String channelName;
    private String channelType;
}