package com.oneforall.padobackend.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "membership_plans")
@Getter
@Setter
@NoArgsConstructor
public class MembershipPlan {
    @Id
    @Column(length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 255)
    private String tagline;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "first_month_discount", nullable = false, length = 120)
    private String firstMonthDiscount = "";

    @Column(name = "benefits_summary", nullable = false, length = 500)
    private String benefitsSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    @Column(length = 80)
    private String badge;

    @Column(nullable = false)
    private boolean highlight;

    @Column(name = "boost_quota", nullable = false)
    private int boostQuota;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipPlanStatus status = MembershipPlanStatus.ACTIVE;

    @ElementCollection
    @CollectionTable(name = "membership_plan_features", joinColumns = @JoinColumn(name = "plan_code"),
            foreignKey = @ForeignKey(name = "fk_membership_plan_features_plan"))
    @Column(name = "feature", nullable = false, length = 500)
    @OrderColumn(name = "display_order")
    private List<String> features = new ArrayList<>();

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
