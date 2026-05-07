package com.retailshop.repository;

import com.retailshop.entity.CampaignAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CampaignAnalyticsRepository extends JpaRepository<CampaignAnalytics, UUID> {

    @Query("""
            select ca
            from CampaignAnalytics ca
            join fetch ca.campaignContent cc
            join fetch cc.campaign c
            where (:campaignId is null or c.id = :campaignId)
              and (:platform is null or ca.platform = :platform)
              and ca.fetchedAt between :fromDate and :toDate
            order by ca.fetchedAt desc
            """)
    List<CampaignAnalytics> findForReport(@Param("campaignId") UUID campaignId,
                                          @Param("platform") com.retailshop.enums.MarketingPlatform platform,
                                          @Param("fromDate") LocalDateTime fromDate,
                                          @Param("toDate") LocalDateTime toDate);
}
