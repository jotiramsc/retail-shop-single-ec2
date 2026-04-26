package com.retailshop.repository;

import com.retailshop.entity.ReceiptSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReceiptSettingsRepository extends JpaRepository<ReceiptSettings, UUID> {
}
