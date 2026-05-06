package com.snickr.repository;

import com.snickr.model.Workspace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

    /**
     * Send workspace invitation
     */
    public void createInvitation(UUID workspaceId, UUID inviterId, String inviteeEmail) {
        String sql = "INSERT INTO workspace_invitations (workspace_id, inviter_id, invitee_email) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, workspaceId, inviterId, inviteeEmail);
    }

    /**
     * Check if an unhandled and unexpired invitation already exists.
     */
    public boolean hasPendingInvitation(UUID workspaceId, String email) {
        String sql = "SELECT COUNT(*) FROM workspace_invitations " +
                "WHERE workspace_id = ? AND invitee_email = ? AND status = 'pending'::status_type AND expiry_at > NOW()";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, workspaceId, email);
        return count != null && count > 0;
    }

    /**
     * Check whether the user associated with this email address is already a workspace member
     */
    public boolean isMemberByEmail(UUID workspaceId, String email) {
        String sql = "SELECT COUNT(*) FROM workspace_memberships wm " +
                "JOIN users u ON wm.user_id = u.user_id " +
                "WHERE wm.workspace_id = ? AND u.email = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, workspaceId, email);
        return count != null && count > 0;
    }

    /**
     * Add users to the workspace and update invitation status
     */
    @Transactional
    public void acceptInvitation(UUID invitationId, UUID workspaceId, UUID userId) {
        // Add the invitee to membership
        String insertMemberSql = "INSERT INTO workspace_memberships (workspace_id, user_id, role) VALUES (?, ?, 'member'::role_type)";
        jdbcTemplate.update(insertMemberSql, workspaceId, userId);

        // invitation status -> "accepted"
        String updateInviteSql = "UPDATE workspace_invitations SET status = 'accepted'::status_type WHERE invitation_id = ?";
        jdbcTemplate.update(updateInviteSql, invitationId);
    }

    /**
     * Query all pending invitations sent to a specific email address
     */
    public List<Map<String, Object>> findPendingInvitationsByEmail(String email) {
        String sql = "SELECT wi.invitation_id, wi.workspace_id, w.name as workspace_name, u.username as inviter_name " +
                "FROM workspace_invitations wi " +
                "JOIN workspaces w ON wi.workspace_id = w.workspace_id " +
                "JOIN users u ON wi.inviter_id = u.user_id " +
                "WHERE wi.invitee_email = ? AND wi.status = 'pending'::status_type AND wi.expiry_at > NOW()";
        return jdbcTemplate.queryForList(sql, email);
    }
}