package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.Role;
import com.oneforall.padobackend.entity.User;

import java.time.Instant;

public record UserResponse(Long id, String email, Role role, Instant createdAt,
                           String locationLabel, String membershipPlanCode, int boostBalance,
                           Instant membershipStartedAt, Instant membershipExpiresAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt(),
                user.getLocationLabel(),
                user.getMembershipPlan() == null ? "FREE" : user.getMembershipPlan().getCode(),
                user.getBoostBalance(), user.getMembershipStartedAt(), user.getMembershipExpiresAt());
    }
}
