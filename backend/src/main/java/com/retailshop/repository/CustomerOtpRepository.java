package com.retailshop.repository;

import com.retailshop.entity.CustomerOtp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOtpRepository extends JpaRepository<CustomerOtp, String> {
}
