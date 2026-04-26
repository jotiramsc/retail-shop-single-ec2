package com.retailshop.repository;

import com.retailshop.entity.Offer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    @Query("""
            select o from Offer o
            left join fetch o.product p
            where o.active = true
            and o.startDate <= :date
            and o.endDate >= :date
            """)
    List<Offer> findActiveOffers(LocalDate date);
}
