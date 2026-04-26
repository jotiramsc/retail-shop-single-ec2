package com.retailshop.repository;

import com.retailshop.entity.CampaignLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CampaignLogRepository extends JpaRepository<CampaignLog, UUID> {

    @EntityGraph(attributePaths = {"campaign", "customer"})
    List<CampaignLog> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"campaign", "customer"})
    Page<CampaignLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"campaign", "customer"})
    List<CampaignLog> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);
}
