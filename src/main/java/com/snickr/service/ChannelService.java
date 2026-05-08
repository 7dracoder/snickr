package com.snickr.service;

import com.snickr.model.Channel;
import com.snickr.repository.ChannelRepository;
import org.springframework.stereotype.Service;

import java.util.List;
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
    public List<Channel> getChannelsForWorkspace(UUID workspaceId) {
        return channelRepository.findChannelsByWorkspaceId(workspaceId);
    }
}