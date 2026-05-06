package com.snickr.service;

import com.snickr.model.Workspace;
import com.snickr.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workspace-related service processing layer
 */
@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Create a new workspace
     */
    public Workspace createWorkspace(String name, String description, UUID creatorId) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("The workspace name cannot be empty.");
        }

        Workspace workspace = new Workspace();
        workspace.setName(name.trim());
        workspace.setDescription(description != null ? description.trim() : null);
        workspace.setCreatorId(creatorId);

        return workspaceRepository.createWorkspace(workspace);
    }

    /**
     * Retrieves the list of all workspaces joined by the specified user.
     */
    public List<Workspace> getWorkspacesForUser(UUID userId) {
        return workspaceRepository.findWorkspacesByUserId(userId);
    }

    /**
     * Verify whether the current user is a member of this workspace
     */
    public Workspace getWorkspaceIfMember(UUID workspaceId, UUID userId) {
        return workspaceRepository.findByIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Unauthorized Access: You do not have permission to access this workspace, or the workspace does not exist."));
    }

    /**
     * Send workspace invitation
     */
    public void inviteUser(UUID workspaceId, UUID inviterId, String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("The invitee's email address cannot be empty");
        }

        String targetEmail = email.trim().toLowerCase();

        if (workspaceRepository.isMemberByEmail(workspaceId, targetEmail)) {
            throw new IllegalArgumentException("member");
        }

        if (workspaceRepository.hasPendingInvitation(workspaceId, targetEmail)) {
            throw new IllegalArgumentException("duplicate");
        }

        workspaceRepository.createInvitation(workspaceId, inviterId, targetEmail);
    }

    /**
     * Retrieve all pending invitations for a specified email address
     */
    public List<Map<String, Object>> getPendingInvitations(String email) {
        if (email == null) return List.of();
        return workspaceRepository.findPendingInvitationsByEmail(email.trim().toLowerCase());
    }

    /**
     * Accept the invitation
     */
    public void acceptInvitation(UUID invitationId, UUID workspaceId, UUID userId) {
        workspaceRepository.acceptInvitation(invitationId, workspaceId, userId);
    }
}