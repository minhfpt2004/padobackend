package com.oneforall.padobackend.repository;

import com.oneforall.padobackend.entity.ChatMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @EntityGraph(attributePaths = "sender")
    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    @EntityGraph(attributePaths = "sender")
    Optional<ChatMessage> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);

    long countByConversationIdAndSenderIdNotAndReadAtIsNull(Long conversationId, Long senderId);

    boolean existsByConversationId(Long conversationId);

    @Modifying
    @Query("""
            update ChatMessage message set message.readAt = :readAt
            where message.conversation.id = :conversationId
              and message.sender.id <> :readerId
              and message.readAt is null
            """)
    int markIncomingAsRead(@Param("conversationId") Long conversationId,
                           @Param("readerId") Long readerId,
                           @Param("readAt") Instant readAt);
}
