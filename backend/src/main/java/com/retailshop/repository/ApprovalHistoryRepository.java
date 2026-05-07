package com.retailshop.repository;

import com.retailshop.entity.ApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, UUID> {
    List<ApprovalHistory> findByCampaignContentIdOrderByActionAtDesc(UUID campaignContentId);
}
