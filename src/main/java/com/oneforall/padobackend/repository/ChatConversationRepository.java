package com.oneforall.padobackend.repository;

import com.oneforall.padobackend.entity.ChatConversation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    @EntityGraph(attributePaths = {"product", "buyer", "seller"})
    Optional<ChatConversation> findByProductIdAndBuyerId(Long productId, Long buyerId);

    @EntityGraph(attributePaths = {"product", "buyer", "seller"})
    @Query("""
            select conversation from ChatConversation conversation
            where conversation.buyer.id = :userId or conversation.seller.id = :userId
            order by conversation.updatedAt desc
            """)
    List<ChatConversation> findAllForParticipant(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"product", "buyer", "seller"})
    @Query("select conversation from ChatConversation conversation where conversation.id = :id")
    Optional<ChatConversation> findDetailedById(@Param("id") Long id);
}
