package com.snickr.service;

import com.snickr.model.Channel;
import com.snickr.repository.ChannelRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Channel-related Business Logic Processing Layer
 */
@Service
public class ChannelService {

    private final ChannelRepository channelRepository;

    public ChannelService(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }


    public Channel createChannel(UUID workspaceId, String name, String type, UUID creatorId) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("The channel name cannot be empty");
        }

        String formattedName = name.trim().toLowerCase().replaceAll("\\s+", "-");

        Channel channel = new Channel();
        channel.setWorkspaceId(workspaceId);
        channel.setName(formattedName);
        channel.setType(type);
        channel.setCreatorId(creatorId);

        return channelRepository.createChannel(channel);
    }

    /**
     * Retrieve all channels within a specific workspace
     */
    public List<Channel> getChannelsForWorkspace(UUID workspaceId, UUID userId) {
        return channelRepository.findChannelsForUserInWorkspace(workspaceId, userId);
    }

    /**
     * Retrieve single channel details
     */
    public Channel getChannelById(UUID channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Cannot find channel with id: " + channelId));
    }

    /**
     * Get or create a direct message channel between two users
     */
    public Channel getOrCreateDirectMessage(UUID workspaceId, UUID creatorId, UUID targetUserId) {
        return channelRepository.findDirectChannelBetweenUsers(workspaceId, creatorId, targetUserId)
                .orElseGet(() -> channelRepository.createDirectChannel(workspaceId, creatorId, targetUserId));
    }

    /**
     * Fetch all DM channels for the sidebar
     */
    public List<Channel> getDirectChannelsForWorkspace(UUID workspaceId, UUID userId) {
        return channelRepository.findDirectChannelsForUser(workspaceId, userId);
    }

    /**
     * Helper to retrieve the other user's name for UI display
     */
    public String getDirectChannelOtherUserName(UUID channelId, UUID userId) {
        return channelRepository.getOtherUsernameInDirectChannel(channelId, userId);
    }

    /**
     * NEW METHODS FOR CHANNEL INVITATIONS
     */
    public void inviteUserToChannel(UUID channelId, UUID inviterId, UUID inviteeId) {
        if (channelRepository.isChannelMember(channelId, inviteeId)) {
            throw new IllegalArgumentException("user_already_member");
        }
        if (channelRepository.hasPendingChannelInvitation(channelId, inviteeId)) {
            throw new IllegalArgumentException("invitation_pending");
        }
        channelRepository.createChannelInvitation(channelId, inviterId, inviteeId);
    }

    public List<Map<String, Object>> getPendingChannelInvitations(UUID workspaceId, UUID userId) {
        return channelRepository.findPendingChannelInvitationsForUser(workspaceId, userId);
    }

    public void acceptChannelInvitation(UUID invitationId, UUID userId) {
        channelRepository.acceptChannelInvitation(invitationId, userId);
    }
}