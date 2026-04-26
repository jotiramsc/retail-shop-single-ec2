package com.retailshop.repository;

import com.retailshop.entity.Offer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    @EntityGraph(attributePaths = {"product"})
    Page<Offer> findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate date, LocalDate sameDate, Pageable pageable);

    @Query("""
            select o from Offer o
            left join fetch o.product p
            where o.active = true
            and o.startDate <= :date
            and o.endDate >= :date
            """)
    List<Offer> findActiveOffers(LocalDate date);

    @Query("""
            select o from Offer o
            left join fetch o.product p
            where o.active = true
            and upper(o.couponCode) = upper(:couponCode)
            and coalesce(o.validFrom, o.startDate) <= :date
            and coalesce(o.validTo, o.endDate) >= :date
            """)
    List<Offer> findActiveCoupon(String couponCode, LocalDate date);
}
