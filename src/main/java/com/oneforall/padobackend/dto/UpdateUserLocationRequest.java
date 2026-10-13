package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.LocationSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateUserLocationRequest(
        @NotBlank @Size(max = 100) String label,
        @NotNull LocationSource source,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @PositiveOrZero Double accuracy
) {
}
