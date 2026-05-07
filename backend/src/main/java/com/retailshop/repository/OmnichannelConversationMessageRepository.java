package com.retailshop.repository;

import com.retailshop.entity.OmnichannelConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OmnichannelConversationMessageRepository extends JpaRepository<OmnichannelConversationMessage, UUID> {
}
