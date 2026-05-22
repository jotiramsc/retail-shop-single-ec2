package com.retailshop.repository;

import com.retailshop.entity.WhatsAppBotTrace;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WhatsAppBotTraceRepository extends JpaRepository<WhatsAppBotTrace, UUID> {
    List<WhatsAppBotTrace> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<WhatsAppBotTrace> findBySentFalseOrderByCreatedAtDesc(Pageable pageable);
    List<WhatsAppBotTrace> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);
}
