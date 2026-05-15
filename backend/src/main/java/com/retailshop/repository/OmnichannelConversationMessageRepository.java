package com.retailshop.repository;

import com.retailshop.entity.OmnichannelConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OmnichannelConversationMessageRepository extends JpaRepository<OmnichannelConversationMessage, UUID> {
    List<OmnichannelConversationMessage> findByConversation_IdOrderByCreatedAtAsc(UUID conversationId);

    Optional<OmnichannelConversationMessage> findFirstByConversation_IdAndDirectionOrderByCreatedAtDesc(UUID conversationId, String direction);

    long countByConversation_IdAndDirectionAndCreatedAtAfter(UUID conversationId, String direction, LocalDateTime createdAt);

    long countByConversation_IdAndDirection(UUID conversationId, String direction);
}
