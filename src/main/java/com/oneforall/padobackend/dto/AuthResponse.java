package com.oneforall.padobackend.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {}
