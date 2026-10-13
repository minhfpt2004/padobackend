package com.oneforall.padobackend.controller;

import com.oneforall.padobackend.dto.ChatConversationResponse;
import com.oneforall.padobackend.dto.ChatMessageResponse;
import com.oneforall.padobackend.dto.CreateConversationRequest;
import com.oneforall.padobackend.dto.MarkMessagesReadResponse;
import com.oneforall.padobackend.dto.SendMessageRequest;
import com.oneforall.padobackend.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/conversations")
    List<ChatConversationResponse> conversations(Authentication authentication) {
        return chatService.findConversations(authentication.getName());
    }

    @PostMapping("/conversations")
    ResponseEntity<ChatConversationResponse> createConversation(
            @Valid @RequestBody CreateConversationRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(chatService.createOrFind(request.productId(), request.buyerId(), authentication.getName()));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    List<ChatMessageResponse> messages(@PathVariable Long conversationId, Authentication authentication) {
        return chatService.findMessages(conversationId, authentication.getName());
    }

    @PostMapping("/conversations/{conversationId}/messages")
    ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.send(conversationId, request.content(), authentication.getName()));
    }

    @PatchMapping("/conversations/{conversationId}/read")
    MarkMessagesReadResponse markRead(@PathVariable Long conversationId, Authentication authentication) {
        return chatService.markRead(conversationId, authentication.getName());
    }
}
