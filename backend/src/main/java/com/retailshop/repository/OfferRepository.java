package com.retailshop.repository;

import com.retailshop.entity.Offer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    @EntityGraph(attributePaths = {"product"})
    Page<Offer> findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate date, LocalDate sameDate, Pageable pageable);

    default List<Offer> findActiveOffers(LocalDate date) {
        return findActiveOffers(date, weekdayCode(date));
    }

    @Query("""
            select o from Offer o
            left join fetch o.product p
            where o.active = true
            and o.startDate <= :date
            and o.endDate >= :date
            and (
                o.scheduleType is null
                or o.scheduleType in ('ALWAYS_ACTIVE', 'ALWAYS', 'DATE_RANGE')
                or o.specificDays is null
                or o.specificDays = ''
                or concat(',', o.specificDays, ',') like concat('%,', :weekday, ',%')
            )
            """)
    List<Offer> findActiveOffers(LocalDate date, String weekday);

    default List<Offer> findActiveCoupon(String couponCode, LocalDate date) {
        return findActiveCoupon(couponCode, date, weekdayCode(date));
    }

    @Query("""
            select o from Offer o
            left join fetch o.product p
            where o.active = true
            and upper(o.couponCode) = upper(:couponCode)
            and coalesce(o.validFrom, o.startDate) <= :date
            and coalesce(o.validTo, o.endDate) >= :date
            and (
                o.scheduleType is null
                or o.scheduleType in ('ALWAYS_ACTIVE', 'ALWAYS', 'DATE_RANGE')
                or o.specificDays is null
                or o.specificDays = ''
                or concat(',', o.specificDays, ',') like concat('%,', :weekday, ',%')
            )
            """)
    List<Offer> findActiveCoupon(String couponCode, LocalDate date, String weekday);

    List<Offer> findByCouponCodeIgnoreCase(String couponCode);

    private static String weekdayCode(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day.name().substring(0, 3).toUpperCase(Locale.ROOT);
    }
}
