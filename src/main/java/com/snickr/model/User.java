package com.snickr.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * User table
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private UUID userId;
    private String email;
    private String username;

    @ToString.Exclude
    private String passwordHash;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}