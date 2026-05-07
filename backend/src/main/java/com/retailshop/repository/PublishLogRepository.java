package com.retailshop.repository;

import com.retailshop.entity.PublishLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PublishLogRepository extends JpaRepository<PublishLog, UUID> {
    List<PublishLog> findByCampaignContentIdOrderByCreatedAtDesc(UUID campaignContentId);
}
