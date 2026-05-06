package com.snickr.controller;

import com.snickr.model.User;
import com.snickr.model.Workspace;
import com.snickr.service.UserService;
import com.snickr.service.WorkspaceService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handle HTTP requests related to workspaces
 */
@Controller
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final UserService userService;

    public WorkspaceController(WorkspaceService workspaceService, UserService userService) {
        this.workspaceService = workspaceService;
        this.userService = userService;
    }

    /**
     * Render the Dashboard page and inject workspace data
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String username = authentication.getName();

        User currentUser = userService.getUserByUsername(username)
                .orElseThrow(() -> new RuntimeException("System Error: Current logged-in user not found"));

        List<Workspace> workspaces = workspaceService.getWorkspacesForUser(currentUser.getUserId());

        List<Map<String, Object>> rawInvitations = workspaceService.getPendingInvitations(currentUser.getEmail());

        List<Map<String, Object>> validInvitations = rawInvitations.stream()
                .filter(invite -> {
                    UUID inviteWorkspaceId = (UUID) invite.get("workspace_id");
                    // The invitation is retained only if none of the IDs in the user's current workspace list match the ID of this invitation
                    return workspaces.stream().noneMatch(w -> w.getWorkspaceId().equals(inviteWorkspaceId));
                })
                .toList();

        model.addAttribute("workspaces", workspaces);
        model.addAttribute("invitations", validInvitations);
        model.addAttribute("currentUser", currentUser);

        return "dashboard";
    }

    /**
     * Workspace creation form submissions
     */
    @PostMapping("/workspaces/create")
    public String createWorkspace(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {

        try {
            String username = authentication.getName();
            User currentUser = userService.getUserByUsername(username)
                    .orElseThrow(() -> new RuntimeException("System Error: Current logged-in user not found"));

            workspaceService.createWorkspace(name, description, currentUser.getUserId());

            return "redirect:/dashboard?created";

        } catch (IllegalArgumentException e) {
            return "redirect:/dashboard?error";
        }
    }

    @GetMapping("/workspaces/{id}")
    public String workspaceDetail(@PathVariable("id") UUID workspaceId, Authentication authentication, Model model) {
        String username = authentication.getName();
        User currentUser = userService.getUserByUsername(username)
                .orElseThrow(() -> new RuntimeException("System Error: Current logged-in user not found"));

        try {
            Workspace currentWorkspace = workspaceService.getWorkspaceIfMember(workspaceId, currentUser.getUserId());

            List<Workspace> allWorkspaces = workspaceService.getWorkspacesForUser(currentUser.getUserId());

            model.addAttribute("workspace", currentWorkspace);
            model.addAttribute("workspaces", allWorkspaces);
            model.addAttribute("currentUser", currentUser);

            return "workspace";

        } catch (IllegalArgumentException e) {
            System.out.println("Attempted unauthorized access detected: " + username + " attempts to access workspace " + workspaceId);
            return "redirect:/dashboard";
        }
    }

    /**
     * Invite other users to workspace by email
     */
    @PostMapping("/workspaces/{id}/invite")
    public String inviteMember(@PathVariable("id") UUID workspaceId,
                               @RequestParam("email") String email,
                               Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userService.getUserByUsername(username).orElseThrow();

        try {
            workspaceService.inviteUser(workspaceId, currentUser.getUserId(), email);
            return "redirect:/workspaces/" + workspaceId + "?invited";
        } catch (IllegalArgumentException e) {
            return "redirect:/workspaces/" + workspaceId + "?inviteError=" + e.getMessage();
        }
    }

    @PostMapping("/invitations/accept")
    public String acceptInvite(@RequestParam("invitationId") UUID invitationId,
                               @RequestParam("workspaceId") UUID workspaceId,
                               Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userService.getUserByUsername(username).orElseThrow();

        try {
            workspaceService.acceptInvitation(invitationId, workspaceId, currentUser.getUserId());
            return "redirect:/dashboard?joined";
        } catch (Exception e) {
            System.out.println("Detected a duplicate attempt to accept an invitation: " + e.getMessage());
            return "redirect:/dashboard";
        }
    }
}