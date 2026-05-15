package com.retailshop.repository;

import com.retailshop.entity.CampaignLeadVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CampaignLeadVisitRepository extends JpaRepository<CampaignLeadVisit, UUID> {
    @Query("""
            select v.source as source, count(v) as visits
            from CampaignLeadVisit v
            where (:campaignId is null or v.campaignId = :campaignId)
              and v.visitedAt between :from and :to
            group by v.source
            order by count(v) desc, v.source asc
            """)
    List<SourceVisitCount> countBySource(@Param("campaignId") UUID campaignId,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

    interface SourceVisitCount {
        String getSource();
        long getVisits();
    }
}
