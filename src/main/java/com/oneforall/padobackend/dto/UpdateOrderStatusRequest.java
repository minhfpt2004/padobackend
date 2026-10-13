package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {
}
