package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.DeliveryType;
import com.oneforall.padobackend.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ProductResponse(
        Long id, String title, String brand, String category, String size, String color,
        String condition, BigDecimal price, BigDecimal originalPrice, BigDecimal shippingFee,
        String description, String reason, String sellerLocation, Double latitude, Double longitude,
        boolean sold, List<String> images, Set<DeliveryType> deliveryOptions,
        Long ownerId, String seller, Instant createdAt, Instant updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getTitle(), product.getBrand(),
                product.getCategory(), product.getSize(), product.getColor(), product.getCondition(),
                product.getPrice(), product.getOriginalPrice(), product.getShippingFee(),
                product.getDescription(), product.getReason(), product.getSellerLocation(),
                product.getLatitude(), product.getLongitude(), product.isSold(),
                List.copyOf(product.getImages()), Set.copyOf(product.getDeliveryOptions()),
                product.getOwner().getId(), product.getOwner().getEmail(),
                product.getCreatedAt(), product.getUpdatedAt());
    }
}
