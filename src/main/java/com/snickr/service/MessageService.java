package com.snickr.service;

import com.snickr.model.Message;
import com.snickr.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    private final MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public void sendMessage(UUID channelId, UUID senderId, String body) {
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalArgumentException("Message body cannot be empty");
        }
        messageRepository.createMessage(channelId, senderId, body.trim());
    }

    public List<Message> getMessagesForChannel(UUID channelId) {
        return messageRepository.findMessagesByChannelId(channelId);
    }

    /**
     * Search messages by keyword within the authorized scope
     */
    public List<Message> searchMessagesInWorkspace(UUID workspaceId, UUID userId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        return messageRepository.searchMessages(workspaceId, userId, keyword.trim());
    }
}