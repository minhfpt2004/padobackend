package com.oneforall.padobackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_owner", columnList = "owner_id"),
        @Index(name = "idx_products_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_products_owner"))
    private User owner;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 80)
    private String brand;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(nullable = false, length = 20)
    private String size;

    @Column(nullable = false, length = 40)
    private String color;

    @Column(nullable = false, length = 40)
    private String condition;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "original_price", precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "seller_location", nullable = false, length = 255)
    private String sellerLocation;

    private Double latitude;
    private Double longitude;

    @Column(nullable = false)
    private boolean sold;

    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"),
            foreignKey = @ForeignKey(name = "fk_product_images_product"))
    @Column(name = "image_url", nullable = false, length = 2048)
    @OrderColumn(name = "display_order")
    private List<String> images = new ArrayList<>();

    @ElementCollection(targetClass = DeliveryType.class)
    @CollectionTable(name = "product_delivery_options", joinColumns = @JoinColumn(name = "product_id"),
            foreignKey = @ForeignKey(name = "fk_product_delivery_product"))
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 20)
    private Set<DeliveryType> deliveryOptions = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
