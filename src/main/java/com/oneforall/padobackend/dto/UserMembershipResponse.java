package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.User;

import java.time.Instant;

public record UserMembershipResponse(
        String planCode,
        String planName,
        int boostBalance,
        Instant startedAt,
        Instant expiresAt
) {
    public static UserMembershipResponse from(User user) {
        String code = user.getMembershipPlan() == null ? "FREE" : user.getMembershipPlan().getCode();
        String name = user.getMembershipPlan() == null ? "FREE" : user.getMembershipPlan().getName();
        return new UserMembershipResponse(code, name, user.getBoostBalance(),
                user.getMembershipStartedAt(), user.getMembershipExpiresAt());
    }
}
