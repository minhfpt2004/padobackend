package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.DeliveryType;
import com.oneforall.padobackend.entity.OrderStatus;
import com.oneforall.padobackend.entity.PurchaseOrder;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        String orderCode,
        Long productId,
        String productTitle,
        String productImageUrl,
        Long buyerId,
        String buyerEmail,
        Long sellerId,
        String sellerEmail,
        BigDecimal itemPrice,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        DeliveryType deliveryType,
        OrderStatus status,
        String buyerName,
        String buyerPhone,
        String deliveryAddress,
        String meetupLocation,
        String note,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        Instant cancelledAt
) {
    public static OrderResponse from(PurchaseOrder order) {
        return new OrderResponse(
                order.getId(),
                "PD-%05d".formatted(order.getId()),
                order.getProductId(),
                order.getProductTitle(),
                order.getProductImageUrl(),
                order.getBuyerId(),
                order.getBuyerEmail(),
                order.getSellerId(),
                order.getSellerEmail(),
                order.getItemPrice(),
                order.getShippingFee(),
                order.getTotalAmount(),
                order.getDeliveryType(),
                order.getStatus(),
                order.getBuyerName(),
                order.getBuyerPhone(),
                order.getDeliveryAddress(),
                order.getMeetupLocation(),
                order.getNote(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getCompletedAt(),
                order.getCancelledAt());
    }
}
