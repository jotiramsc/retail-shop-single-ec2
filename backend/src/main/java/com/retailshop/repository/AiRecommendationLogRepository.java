package com.retailshop.repository;

import com.retailshop.entity.AiRecommendationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiRecommendationLogRepository extends JpaRepository<AiRecommendationLog, UUID> {
}
