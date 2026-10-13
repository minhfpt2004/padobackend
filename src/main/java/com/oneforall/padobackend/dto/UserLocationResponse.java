package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.LocationSource;
import com.oneforall.padobackend.entity.User;

import java.time.Instant;

public record UserLocationResponse(
        boolean configured,
        String label,
        LocationSource source,
        Double latitude,
        Double longitude,
        Double accuracy,
        Instant updatedAt
) {
    public static UserLocationResponse from(User user) {
        return new UserLocationResponse(
                user.getLocationSource() != null,
                user.getLocationLabel(),
                user.getLocationSource(),
                user.getLocationLatitude(),
                user.getLocationLongitude(),
                user.getLocationAccuracy(),
                user.getLocationUpdatedAt());
    }
}
