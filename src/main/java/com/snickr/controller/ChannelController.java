package com.snickr.controller;

import com.snickr.model.Channel;
import com.snickr.model.User;
import com.snickr.model.Workspace;
import com.snickr.service.ChannelService;
import com.snickr.service.UserService;
import com.snickr.service.WorkspaceService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Handle HTTP requests related to channels
 */
@Controller
public class ChannelController {

    private final ChannelService channelService;
    private final WorkspaceService workspaceService;
    private final UserService userService;

    public ChannelController(ChannelService channelService, WorkspaceService workspaceService, UserService userService) {
        this.channelService = channelService;
        this.workspaceService = workspaceService;
        this.userService = userService;
    }

    /**
     * Enter the specific channel page
     */
    @GetMapping("/workspaces/{workspaceId}/channels/{channelId}")
    public String channelDetail(@PathVariable("workspaceId") UUID workspaceId,
                                @PathVariable("channelId") UUID channelId,
                                Authentication authentication, Model model) {
        String username = authentication.getName();
        User currentUser = userService.getUserByUsername(username).orElseThrow();

        try {
            Workspace currentWorkspace = workspaceService.getWorkspaceIfMember(workspaceId, currentUser.getUserId());
            List<Workspace> allWorkspaces = workspaceService.getWorkspacesForUser(currentUser.getUserId());
            List<Channel> channels = channelService.getChannelsForWorkspace(workspaceId);

            Channel activeChannel = channelService.getChannelById(channelId);

            model.addAttribute("workspace", currentWorkspace);
            model.addAttribute("workspaces", allWorkspaces);
            model.addAttribute("channels", channels);
            model.addAttribute("activeChannel", activeChannel);
            model.addAttribute("currentUser", currentUser);

            return "workspace";
        } catch (IllegalArgumentException e) {
            return "redirect:/dashboard";
        }
    }

    /**
     * Handling channel creation form submissions
     */
    @PostMapping("/workspaces/{id}/channels/create")
    public String createChannel(@PathVariable("id") UUID workspaceId,
                                @RequestParam("name") String name,
                                @RequestParam("type") String type,
                                Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userService.getUserByUsername(username).orElseThrow();

        try {
            workspaceService.getWorkspaceIfMember(workspaceId, currentUser.getUserId());

            channelService.createChannel(workspaceId, name, type, currentUser.getUserId());

            return "redirect:/workspaces/" + workspaceId + "?channelCreated";
        } catch (IllegalArgumentException e) {
            return "redirect:/workspaces/" + workspaceId + "?channelError=" + e.getMessage();
        }
    }
}