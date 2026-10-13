package com.oneforall.padobackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateConversationRequest(
        @NotNull @Positive Long productId,
        @Positive Long buyerId
) {
}
