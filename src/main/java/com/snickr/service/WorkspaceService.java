package com.snickr.service;

import com.snickr.model.Workspace;
import com.snickr.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
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
}