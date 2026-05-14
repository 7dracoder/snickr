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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Handles global search requests
 */
@Controller
public class SearchController {

    private final WorkspaceService workspaceService;
    private final ChannelService channelService;
    private final UserService userService;
    private final MessageService messageService;

    public SearchController(WorkspaceService workspaceService, ChannelService channelService,
                            UserService userService, MessageService messageService) {
        this.workspaceService = workspaceService;
        this.channelService = channelService;
        this.userService = userService;
        this.messageService = messageService;
    }

    @GetMapping("/workspaces/{workspaceId}/search")
    public String search(@PathVariable("workspaceId") UUID workspaceId,
                         @RequestParam("q") String keyword,
                         Authentication authentication, Model model) {
        String username = authentication.getName();
        User currentUser = userService.getUserByUsername(username).orElseThrow();

        try {
            // Verify workspace membership
            Workspace currentWorkspace = workspaceService.getWorkspaceIfMember(workspaceId, currentUser.getUserId());
            List<Workspace> allWorkspaces = workspaceService.getWorkspacesForUser(currentUser.getUserId());
            List<Channel> channels = channelService.getChannelsForWorkspace(workspaceId, currentUser.getUserId());

            // Perform global search
            List<Message> searchResults = messageService.searchMessagesInWorkspace(workspaceId, currentUser.getUserId(), keyword);

            model.addAttribute("workspace", currentWorkspace);
            model.addAttribute("workspaces", allWorkspaces);
            model.addAttribute("channels", channels);
            model.addAttribute("activeChannel", null);

            // Add search specific attributes
            model.addAttribute("searchResults", searchResults);
            model.addAttribute("searchKeyword", keyword);
            model.addAttribute("currentUser", currentUser);

            return "workspace";
        } catch (IllegalArgumentException e) {
            return "redirect:/dashboard";
        }
    }
}