package com.retailshop.repository;

import com.retailshop.entity.OmnichannelConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OmnichannelConversationRepository extends JpaRepository<OmnichannelConversation, UUID> {
    Optional<OmnichannelConversation> findFirstByLead_IdAndChannelOrderByUpdatedAtDesc(UUID leadId, String channel);
}
