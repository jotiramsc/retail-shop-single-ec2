package com.retailshop.repository;

import com.retailshop.entity.CampaignLeadVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CampaignLeadVisitRepository extends JpaRepository<CampaignLeadVisit, UUID> {
}
