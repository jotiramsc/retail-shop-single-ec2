package com.retailshop.repository;

import com.retailshop.entity.SiteVisit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteVisitRepository extends JpaRepository<SiteVisit, UUID> {

    interface DailyVisitProjection {
        LocalDate getVisitDate();
        Long getVisits();
    }

    interface SourceBreakdownProjection {
        String getSourceType();
        String getSourceLabel();
        Long getVisits();
    }

    interface LabelCountProjection {
        String getLabel();
        Long getVisits();
    }

    interface CountryBreakdownProjection {
        String getCountryName();
        String getCountryCode();
        Long getVisits();
    }

    Optional<SiteVisit> findByVisitorIdAndVisitDate(String visitorId, LocalDate visitDate);

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<SiteVisit> findAllByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    List<SiteVisit> findAllByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(LocalDateTime since, Pageable pageable);

    @Query("""
            select v.visitDate as visitDate, count(v) as visits
            from SiteVisit v
            where v.visitDate between :from and :to
            group by v.visitDate
            order by v.visitDate asc
            """)
    List<DailyVisitProjection> countDailyBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select v.sourceType as sourceType, v.sourceLabel as sourceLabel, count(v) as visits
            from SiteVisit v
            where v.visitDate between :from and :to
            group by v.sourceType, v.sourceLabel
            order by count(v) desc, v.sourceLabel asc
            """)
    List<SourceBreakdownProjection> summarizeSources(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select case
                when v.referrerHost is null or v.referrerHost = '' then 'Direct'
                else v.referrerHost
            end as label,
            count(v) as visits
            from SiteVisit v
            where v.visitDate between :from and :to
            group by case
                when v.referrerHost is null or v.referrerHost = '' then 'Direct'
                else v.referrerHost
            end
            order by count(v) desc,
            case
                when v.referrerHost is null or v.referrerHost = '' then 'Direct'
                else v.referrerHost
            end asc
            """)
    List<LabelCountProjection> summarizeReferrers(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select v.landingPath as label, count(v) as visits
            from SiteVisit v
            where v.visitDate between :from and :to
            group by v.landingPath
            order by count(v) desc, v.landingPath asc
            """)
    List<LabelCountProjection> summarizeLandingPages(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select v.countryName as countryName, v.countryCode as countryCode, count(v) as visits
            from SiteVisit v
            where v.visitDate between :from and :to
              and v.countryName is not null
              and v.countryName <> ''
            group by v.countryName, v.countryCode
            order by count(v) desc, v.countryName asc
            """)
    List<CountryBreakdownProjection> summarizeCountries(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
