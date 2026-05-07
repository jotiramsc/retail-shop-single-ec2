package com.retailshop.repository;

import com.retailshop.entity.CampaignContent;
import com.retailshop.enums.MarketingContentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignContentRepository extends JpaRepository<CampaignContent, UUID> {

    @EntityGraph(attributePaths = {"campaign"})
    List<CampaignContent> findByCampaignIdOrderByCreatedAtAsc(UUID campaignId);

    @EntityGraph(attributePaths = {"campaign"})
    List<CampaignContent> findByStatusOrderByCreatedAtAsc(MarketingContentStatus status);

    @EntityGraph(attributePaths = {"campaign"})
    List<CampaignContent> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            MarketingContentStatus status,
            LocalDateTime scheduledAt
    );

    @Override
    @EntityGraph(attributePaths = {"campaign"})
    Optional<CampaignContent> findById(UUID id);
}
