package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.BillingCycle;
import com.oneforall.padobackend.entity.MembershipPlan;
import com.oneforall.padobackend.entity.MembershipPlanStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MembershipPlanResponse(
        String code,
        String name,
        String tagline,
        BigDecimal price,
        String firstMonthDiscount,
        String benefitsSummary,
        BillingCycle billingCycle,
        List<String> features,
        String badge,
        boolean highlight,
        int boostQuota,
        MembershipPlanStatus status,
        long subscribersCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static MembershipPlanResponse from(MembershipPlan plan, long subscribersCount) {
        return new MembershipPlanResponse(plan.getCode(), plan.getName(), plan.getTagline(), plan.getPrice(),
                plan.getFirstMonthDiscount(), plan.getBenefitsSummary(), plan.getBillingCycle(),
                List.copyOf(plan.getFeatures()), plan.getBadge(), plan.isHighlight(), plan.getBoostQuota(),
                plan.getStatus(), subscribersCount, plan.getCreatedAt(), plan.getUpdatedAt());
    }
}
