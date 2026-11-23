package com.oneforall.padobackend.service;

import com.oneforall.padobackend.dto.ChatConversationResponse;
import com.oneforall.padobackend.dto.ChatMessageResponse;
import com.oneforall.padobackend.dto.MarkMessagesReadResponse;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.entity.ChatConversation;
import com.oneforall.padobackend.entity.ChatMessage;
import com.oneforall.padobackend.entity.Product;
import com.oneforall.padobackend.entity.Role;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.exception.BadRequestException;
import com.oneforall.padobackend.exception.ConflictException;
import com.oneforall.padobackend.exception.ForbiddenException;
import com.oneforall.padobackend.exception.ResourceNotFoundException;
import com.oneforall.padobackend.repository.ChatConversationRepository;
import com.oneforall.padobackend.repository.ChatMessageRepository;
import com.oneforall.padobackend.repository.OrderRepository;
import com.oneforall.padobackend.repository.ProductRepository;
import com.oneforall.padobackend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {
    private final ChatConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final ProductRepository products;
    private final UserRepository users;
    private final OrderRepository orders;
    private final AuditService auditService;

    public ChatService(ChatConversationRepository conversations, ChatMessageRepository messages,
                       ProductRepository products, UserRepository users, OrderRepository orders,
                       AuditService auditService) {
        this.conversations = conversations;
        this.messages = messages;
        this.products = products;
        this.users = users;
        this.orders = orders;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ChatConversationResponse> findConversations(String email) {
        User actor = requireUser(email);
        return conversations.findAllForParticipant(actor.getId()).stream()
                .map(conversation -> summarize(conversation, actor.getId()))
                .toList();
    }

    @Transactional
    public ChatConversationResponse createOrFind(Long productId, Long requestedBuyerId, String email) {
        User actor = requireUser(email);
        Product product = products.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (product.isModerationHidden()) {
            throw new ConflictException("Product is not available for new conversations");
        }

        User buyer;
        if (product.getOwner().getId().equals(actor.getId())) {
            if (requestedBuyerId == null) {
                throw new ConflictException("You cannot start a conversation for your own product without a buyer");
            }
            if (!orders.existsByProductIdAndBuyerIdAndSellerId(productId, requestedBuyerId, actor.getId())) {
                throw new ForbiddenException("You can only contact a buyer who ordered this product");
            }
            buyer = users.findById(requestedBuyerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Buyer not found"));
        } else {
            if (requestedBuyerId != null && !requestedBuyerId.equals(actor.getId())) {
                throw new BadRequestException("buyerId must match the signed-in buyer");
            }
            buyer = actor;
        }

        ChatConversation conversation = conversations.findByProductIdAndBuyerId(productId, buyer.getId())
                .orElse(null);
        if (conversation == null) {
            conversation = new ChatConversation();
            conversation.setProduct(product);
            conversation.setBuyer(buyer);
            conversation.setSeller(product.getOwner());
            conversation = conversations.save(conversation);
            auditService.recordSuccess(AuditEventType.CONVERSATION_CREATED, actor,
                    AuditTargetType.CONVERSATION, conversation.getId(), Map.of(
                            "productId", product.getId(),
                            "buyerUserId", buyer.getId(),
                            "sellerUserId", product.getOwner().getId()
                    ));
        }
        return summarize(conversation, actor.getId());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> findMessages(Long conversationId, String email) {
        User actor = requireUser(email);
        ChatConversation conversation = requireConversation(conversationId);
        verifyParticipant(conversation, actor);
        return messages.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(message -> ChatMessageResponse.from(message, actor.getId()))
                .toList();
    }

    @Transactional
    public ChatMessageResponse send(Long conversationId, String content, String email) {
        User actor = requireUser(email);
        ChatConversation conversation = requireConversation(conversationId);
        verifyParticipant(conversation, actor);

        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(actor);
        message.setContent(content.trim());
        ChatMessage saved = messages.save(message);
        conversation.touch();
        conversations.save(conversation);
        auditService.recordSuccess(AuditEventType.CHAT_MESSAGE_SENT, actor,
                AuditTargetType.MESSAGE, saved.getId(), Map.of(
                        "conversationId", conversation.getId(),
                        "contentLength", saved.getContent().length()
                ));
        return ChatMessageResponse.from(saved, actor.getId());
    }

    @Transactional
    public MarkMessagesReadResponse markRead(Long conversationId, String email) {
        User actor = requireUser(email);
        ChatConversation conversation = requireConversation(conversationId);
        verifyParticipant(conversation, actor);
        int updated = messages.markIncomingAsRead(conversationId, actor.getId(), Instant.now());
        if (updated > 0) {
            auditService.recordSuccess(AuditEventType.CHAT_MESSAGES_READ, actor,
                    AuditTargetType.CONVERSATION, conversationId, Map.of("messageCount", updated));
        }
        return new MarkMessagesReadResponse(updated);
    }

    private ChatConversationResponse summarize(ChatConversation conversation, Long actorId) {
        ChatMessage lastMessage = messages.findTopByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .orElse(null);
        long unreadCount = messages.countByConversationIdAndSenderIdNotAndReadAtIsNull(
                conversation.getId(), actorId);
        return ChatConversationResponse.from(conversation, lastMessage, unreadCount, actorId);
    }

    private ChatConversation requireConversation(Long id) {
        return conversations.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
    }

    private User requireUser(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void verifyParticipant(ChatConversation conversation, User actor) {
        if (actor.getRole() != Role.ADMIN
                && !conversation.getBuyer().getId().equals(actor.getId())
                && !conversation.getSeller().getId().equals(actor.getId())) {
            throw new ForbiddenException("You do not have access to this conversation");
        }
    }
}
