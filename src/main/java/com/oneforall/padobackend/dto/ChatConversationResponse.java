package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.ChatConversation;
import com.oneforall.padobackend.entity.ChatMessage;
import com.oneforall.padobackend.entity.Product;
import com.oneforall.padobackend.entity.User;

import java.math.BigDecimal;
import java.time.Instant;

public record ChatConversationResponse(
        Long id,
        Long productId,
        String productTitle,
        String productImageUrl,
        BigDecimal productPrice,
        BigDecimal productOriginalPrice,
        String productSize,
        boolean productSold,
        String sellerLocation,
        Long buyerId,
        String buyerEmail,
        Long sellerId,
        String sellerEmail,
        String currentUserRole,
        Long otherParticipantId,
        String otherParticipantEmail,
        String lastMessage,
        Instant lastMessageAt,
        long unreadCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ChatConversationResponse from(ChatConversation conversation, ChatMessage lastMessage,
                                                long unreadCount, Long actorId) {
        Product product = conversation.getProduct();
        boolean buyerView = conversation.getBuyer().getId().equals(actorId);
        User other = buyerView ? conversation.getSeller() : conversation.getBuyer();
        return new ChatConversationResponse(
                conversation.getId(),
                product.getId(),
                product.getTitle(),
                product.getImages().isEmpty() ? null : product.getImages().getFirst(),
                product.getPrice(),
                product.getOriginalPrice(),
                product.getSize(),
                product.isSold(),
                product.getSellerLocation(),
                conversation.getBuyer().getId(),
                conversation.getBuyer().getEmail(),
                conversation.getSeller().getId(),
                conversation.getSeller().getEmail(),
                buyerView ? "BUYER" : "SELLER",
                other.getId(),
                other.getEmail(),
                lastMessage == null ? null : lastMessage.getContent(),
                lastMessage == null ? null : lastMessage.getCreatedAt(),
                unreadCount,
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }
}
