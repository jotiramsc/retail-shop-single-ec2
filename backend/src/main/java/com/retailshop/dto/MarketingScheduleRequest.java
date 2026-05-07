package com.retailshop.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MarketingScheduleRequest {

    @NotNull
    private LocalDateTime scheduledAt;
}
