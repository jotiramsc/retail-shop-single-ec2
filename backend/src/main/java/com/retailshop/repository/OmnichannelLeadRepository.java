package com.retailshop.repository;

import com.retailshop.entity.OmnichannelLead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OmnichannelLeadRepository extends JpaRepository<OmnichannelLead, UUID> {
    Optional<OmnichannelLead> findFirstByChannelAndExternalUserIdOrderByUpdatedAtDesc(String channel, String externalUserId);
}
