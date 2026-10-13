package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateMembershipPaymentRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{2,40}") String planCode,
        @NotNull PaymentMethod paymentMethod
) {}
