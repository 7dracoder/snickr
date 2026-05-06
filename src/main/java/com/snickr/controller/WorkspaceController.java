package com.snickr.controller;

import com.snickr.model.User;
import com.snickr.model.Workspace;
import com.snickr.service.UserService;
import com.snickr.service.WorkspaceService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

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

        model.addAttribute("workspaces", workspaces);
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
}