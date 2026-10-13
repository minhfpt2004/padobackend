package com.oneforall.padobackend.dto;

public record LocationSuggestionResponse(
        String placeId,
        String label,
        double latitude,
        double longitude
) {
}
