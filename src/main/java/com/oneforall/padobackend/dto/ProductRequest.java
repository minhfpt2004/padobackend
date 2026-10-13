package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.DeliveryType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record ProductRequest(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 80) String brand,
        @NotBlank @Size(max = 60) String category,
        @NotBlank @Size(max = 20) String size,
        @NotBlank @Size(max = 40) String color,
        @NotBlank @Size(max = 40) String condition,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal originalPrice,
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal shippingFee,
        @NotBlank @Size(max = 5000) String description,
        @Size(max = 2000) String reason,
        @NotBlank @Size(max = 255) String sellerLocation,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        boolean sold,
        @NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 2048) String> images,
        @NotEmpty Set<DeliveryType> deliveryOptions
) {}
