package com.retailshop.repository;

import com.retailshop.entity.ProductCategoryOption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryOptionRepository extends JpaRepository<ProductCategoryOption, UUID> {
    List<ProductCategoryOption> findByActiveTrueOrderByDisplayNameAsc();
    Page<ProductCategoryOption> findAllByOrderByDisplayNameAsc(Pageable pageable);
    Optional<ProductCategoryOption> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByDisplayNameIgnoreCase(String displayName);
}
