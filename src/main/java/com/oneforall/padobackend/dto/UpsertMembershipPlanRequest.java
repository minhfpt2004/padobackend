package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.BillingCycle;
import com.oneforall.padobackend.entity.MembershipPlanStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record UpsertMembershipPlanRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{2,40}") String code,
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 255) String tagline,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @Size(max = 120) String firstMonthDiscount,
        @NotBlank @Size(max = 500) String benefitsSummary,
        @NotNull BillingCycle billingCycle,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 500) String> features,
        @Size(max = 80) String badge,
        boolean highlight,
        @PositiveOrZero @Max(10000) int boostQuota,
        @NotNull MembershipPlanStatus status
) {}
