package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.ChatMessage;

import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderEmail,
        String content,
        boolean mine,
        Instant createdAt,
        Instant readAt
) {
    public static ChatMessageResponse from(ChatMessage message, Long actorId) {
        return new ChatMessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getId(),
                message.getSender().getEmail(),
                message.getContent(),
                message.getSender().getId().equals(actorId),
                message.getCreatedAt(),
                message.getReadAt());
    }
}
