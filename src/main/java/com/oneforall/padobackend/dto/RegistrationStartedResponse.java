package com.oneforall.padobackend.dto;

public record RegistrationStartedResponse(
        String email,
        long expiresInSeconds,
        long resendAfterSeconds,
        String deliveryMode,
        String developmentOtp
) {}
