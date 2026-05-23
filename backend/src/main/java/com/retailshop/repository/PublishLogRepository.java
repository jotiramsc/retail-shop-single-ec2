package com.retailshop.repository;

import com.retailshop.entity.PublishLog;
import com.retailshop.enums.MarketingPlatform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PublishLogRepository extends JpaRepository<PublishLog, UUID> {
    List<PublishLog> findByCampaignContentIdOrderByCreatedAtDesc(UUID campaignContentId);

    @Query("""
            select count(log)
            from PublishLog log
            where log.platform = :platform
              and upper(log.status) = upper(:status)
              and log.createdAt between :start and :end
            """)
    long countByPlatformAndStatusBetween(@Param("platform") MarketingPlatform platform,
                                         @Param("status") String status,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);
}
