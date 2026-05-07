package com.retailshop.repository;

import com.retailshop.entity.SocialWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SocialWebhookEventRepository extends JpaRepository<SocialWebhookEvent, UUID> {
}
