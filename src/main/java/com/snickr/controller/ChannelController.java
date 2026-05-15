package com.snickr.controller;

import com.snickr.model.Channel;
import com.snickr.model.Message;
import com.snickr.model.User;
import com.snickr.model.Workspace;
import com.snickr.service.ChannelService;
import com.snickr.service.MessageService;
import com.snickr.service.UserService;
import com.snickr.service.WorkspaceService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for handling Channel and Direct Message requests
 */
@Controller
public class ChannelController {

    private final ChannelService channelService;
    private final WorkspaceService workspaceService;
    private final UserService userService;
    private final MessageService messageService;

    public ChannelController(ChannelService channelService, WorkspaceService workspaceService,
                             UserService userService, MessageService messageService) {
        this.channelService = channelService;
        this.workspaceService = workspaceService;
        this.userService = userService;
        this.messageService = messageService;
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
            List<Channel> channels = channelService.getChannelsForWorkspace(workspaceId, currentUser.getUserId());

            // Fetch Workspace Members and Direct Channels for the Sidebar
            List<User> workspaceMembers = userService.getUsersInWorkspace(workspaceId);
            List<Channel> directChannels = channelService.getDirectChannelsForWorkspace(workspaceId, currentUser.getUserId());

            Channel activeChannel = channelService.getChannelById(channelId);

            // Access control for private channels
            if ("private".equals(activeChannel.getType())) {
                boolean hasAccess = channels.stream().anyMatch(c -> c.getChannelId().equals(activeChannel.getChannelId()));
                if (!hasAccess) {
                    throw new IllegalArgumentException("Access denied.");
                }
            }

            // Dynamically set DM name to the other user's username for UI display
            if ("direct".equals(activeChannel.getType())) {
                String otherUsername = channelService.getDirectChannelOtherUserName(channelId, currentUser.getUserId());
                activeChannel.setName(otherUsername);
            }

            List<Message> messages = messageService.getMessagesForChannel(channelId);

            model.addAttribute("workspace", currentWorkspace);
            model.addAttribute("workspaces", allWorkspaces);
            model.addAttribute("channels", channels);
            model.addAttribute("workspaceMembers", workspaceMembers);
            model.addAttribute("directChannels", directChannels);
            model.addAttribute("activeChannel", activeChannel);
            model.addAttribute("messages", messages);
            model.addAttribute("currentUser", currentUser);

            return "workspace";
        } catch (IllegalArgumentException e) {
            return "redirect:/workspaces/" + workspaceId;
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

    /**
     * Send message
     */
    @PostMapping("/workspaces/{workspaceId}/channels/{channelId}/messages")
    public String sendMessage(@PathVariable("workspaceId") UUID workspaceId,
                              @PathVariable("channelId") UUID channelId,
                              @RequestParam("body") String body,
                              Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userService.getUserByUsername(username).orElseThrow();

        try {
            workspaceService.getWorkspaceIfMember(workspaceId, currentUser.getUserId());

            messageService.sendMessage(channelId, currentUser.getUserId(), body);

            return "redirect:/workspaces/" + workspaceId + "/channels/" + channelId;
        } catch (IllegalArgumentException e) {
            return "redirect:/workspaces/" + workspaceId + "/channels/" + channelId + "?error=empty";
        }
    }

    /**
     * Handle creating or opening a Direct Message
     */
    @PostMapping("/workspaces/{id}/dms/create")
    public String createDirectMessage(@PathVariable("id") UUID workspaceId,
                                      @RequestParam("targetUserId") UUID targetUserId,
                                      Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userService.getUserByUsername(username).orElseThrow();

        try {
            workspaceService.getWorkspaceIfMember(workspaceId, currentUser.getUserId());
            Channel dmChannel = channelService.getOrCreateDirectMessage(workspaceId, currentUser.getUserId(), targetUserId);

            return "redirect:/workspaces/" + workspaceId + "/channels/" + dmChannel.getChannelId();
        } catch (Exception e) {
            return "redirect:/workspaces/" + workspaceId + "?error=dm_failed";
        }
    }
}