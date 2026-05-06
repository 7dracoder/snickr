package com.snickr.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Channel table
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Channel {

    private UUID channelId;
    private UUID workspaceId;
    private String name;

    // Use String to map the enum type `channel_type`
    private String type;

    private UUID creatorId;
    private OffsetDateTime createdAt;
}