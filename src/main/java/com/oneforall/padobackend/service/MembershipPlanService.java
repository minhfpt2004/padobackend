package com.oneforall.padobackend.service;

import com.oneforall.padobackend.dto.MembershipPlanResponse;
import com.oneforall.padobackend.dto.UpsertMembershipPlanRequest;
import com.oneforall.padobackend.entity.MembershipPlan;
import com.oneforall.padobackend.entity.MembershipPlanStatus;
import com.oneforall.padobackend.exception.BadRequestException;
import com.oneforall.padobackend.exception.ResourceNotFoundException;
import com.oneforall.padobackend.repository.MembershipPlanRepository;
import com.oneforall.padobackend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class MembershipPlanService {
    private final MembershipPlanRepository plans;
    private final UserRepository users;

    public MembershipPlanService(MembershipPlanRepository plans, UserRepository users) {
        this.plans = plans;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<MembershipPlanResponse> activePlans() {
        return plans.findByStatusOrderByPriceAsc(MembershipPlanStatus.ACTIVE).stream()
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<MembershipPlanResponse> allPlans() {
        return plans.findAllByOrderByPriceAsc().stream().map(this::response).toList();
    }

    @Transactional
    public MembershipPlanResponse create(UpsertMembershipPlanRequest request) {
        String code = normalize(request.code());
        if (plans.existsById(code)) throw new BadRequestException("Membership plan code already exists");
        MembershipPlan plan = new MembershipPlan();
        plan.setCode(code);
        apply(plan, request);
        return response(plans.save(plan));
    }

    @Transactional
    public MembershipPlanResponse update(String code, UpsertMembershipPlanRequest request) {
        String normalized = normalize(code);
        if (!normalized.equals(normalize(request.code()))) {
            throw new BadRequestException("Plan code in path and request must match");
        }
        if ("FREE".equals(normalized) && request.status() == MembershipPlanStatus.HIDDEN) {
            throw new BadRequestException("FREE plan cannot be hidden");
        }
        MembershipPlan plan = requirePlan(normalized);
        apply(plan, request);
        return response(plans.save(plan));
    }

    @Transactional
    public MembershipPlanResponse hide(String code) {
        MembershipPlan plan = requirePlan(normalize(code));
        if ("FREE".equals(plan.getCode())) throw new BadRequestException("FREE plan cannot be hidden");
        plan.setStatus(MembershipPlanStatus.HIDDEN);
        return response(plans.save(plan));
    }

    private void apply(MembershipPlan plan, UpsertMembershipPlanRequest request) {
        plan.setName(request.name().trim());
        plan.setTagline(request.tagline().trim());
        plan.setPrice(request.price());
        plan.setFirstMonthDiscount(request.firstMonthDiscount().trim());
        plan.setBenefitsSummary(request.benefitsSummary().trim());
        plan.setBillingCycle(request.billingCycle());
        plan.setBadge(request.badge() == null || request.badge().isBlank() ? null : request.badge().trim());
        plan.setHighlight(request.highlight());
        plan.setBoostQuota(request.boostQuota());
        plan.setStatus(request.status());
        plan.getFeatures().clear();
        plan.getFeatures().addAll(request.features().stream().map(String::trim).toList());
    }

    private MembershipPlan requirePlan(String code) {
        return plans.findById(code).orElseThrow(() -> new ResourceNotFoundException("Membership plan not found"));
    }

    private MembershipPlanResponse response(MembershipPlan plan) {
        return MembershipPlanResponse.from(plan, users.countByMembershipPlan_Code(plan.getCode()));
    }

    private String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
