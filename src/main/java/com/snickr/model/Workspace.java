package com.snickr.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Workspaces table
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Workspace {
    private UUID workspaceId;
    private String name;
    private String description;
    private UUID creatorId;
    private OffsetDateTime createdAt;
}