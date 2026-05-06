package com.snickr.repository;

import com.snickr.model.Workspace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Responsible for handling database interactions for the `workspaces` and `workspace_memberships` tables
 */
@Repository
public class WorkspaceRepository {

    private final JdbcTemplate jdbcTemplate;

    public WorkspaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Workspace> workspaceRowMapper = (rs, rowNum) -> {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceId(rs.getObject("workspace_id", UUID.class));
        workspace.setName(rs.getString("name"));
        workspace.setDescription(rs.getString("description"));
        workspace.setCreatorId(rs.getObject("creator_id", UUID.class));
        workspace.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        return workspace;
    };

    /**
     * Create a workspace and automatically set the creator as the administrator
     */
    @Transactional
    public Workspace createWorkspace(Workspace workspace) {

        // Create a workspace and automatically set the creator as the administrator
        String insertWorkspaceSql = "INSERT INTO workspaces (name, description, creator_id) VALUES (?, ?, ?) RETURNING *";
        Workspace newWorkspace = jdbcTemplate.queryForObject(
                insertWorkspaceSql,
                workspaceRowMapper,
                workspace.getName(),
                workspace.getDescription(),
                workspace.getCreatorId()
        );

        // Add the creator as an administrator in the `workspace_memberships` table
        String insertMembershipSql = "INSERT INTO workspace_memberships (workspace_id, user_id, role) VALUES (?, ?, 'administrator'::role_type)";
        jdbcTemplate.update(
                insertMembershipSql,
                newWorkspace.getWorkspaceId(),
                newWorkspace.getCreatorId()
        );

        return newWorkspace;
    }

    /**
     * Query all workspaces a specific user has joined
     */
    public List<Workspace> findWorkspacesByUserId(UUID userId) {
        String sql = "SELECT w.* FROM workspaces w " +
                "JOIN workspace_memberships wm ON w.workspace_id = wm.workspace_id " +
                "WHERE wm.user_id = ?";
        return jdbcTemplate.query(sql, workspaceRowMapper, userId);
    }

    /**
     * Workspace authentication
     */
    public Optional<Workspace> findByIdAndUserId(UUID workspaceId, UUID userId) {
        String sql = "SELECT w.* FROM workspaces w " +
                "JOIN workspace_memberships wm ON w.workspace_id = wm.workspace_id " +
                "WHERE w.workspace_id = ? AND wm.user_id = ?";
        List<Workspace> workspaces = jdbcTemplate.query(sql, workspaceRowMapper, workspaceId, userId);
        return workspaces.stream().findFirst();
    }
}