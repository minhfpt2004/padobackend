package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.DeliveryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotNull @Positive Long productId,
        @NotNull DeliveryType deliveryType,
        @NotBlank @Size(max = 100) String buyerName,
        @NotBlank @Size(max = 30) String buyerPhone,
        @Size(max = 500) String deliveryAddress,
        @Size(max = 500) String meetupLocation,
        @Size(max = 1000) String note,
        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") Double buyerLatitude,
        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") Double buyerLongitude
) {
}
