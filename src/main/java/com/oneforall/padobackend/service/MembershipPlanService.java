package com.oneforall.padobackend.service;

import com.oneforall.padobackend.dto.MembershipPlanResponse;
import com.oneforall.padobackend.dto.UpsertMembershipPlanRequest;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.entity.MembershipPlan;
import com.oneforall.padobackend.entity.MembershipPlanStatus;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.exception.BadRequestException;
import com.oneforall.padobackend.exception.ResourceNotFoundException;
import com.oneforall.padobackend.repository.MembershipPlanRepository;
import com.oneforall.padobackend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MembershipPlanService {
    private final MembershipPlanRepository plans;
    private final UserRepository users;
    private final AuditService auditService;

    public MembershipPlanService(MembershipPlanRepository plans, UserRepository users,
                                 AuditService auditService) {
        this.plans = plans;
        this.users = users;
        this.auditService = auditService;
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
    public MembershipPlanResponse create(UpsertMembershipPlanRequest request, String adminEmail) {
        User actor = requireUser(adminEmail);
        String code = normalize(request.code());
        if (plans.existsById(code)) throw new BadRequestException("Membership plan code already exists");
        MembershipPlan plan = new MembershipPlan();
        plan.setCode(code);
        apply(plan, request);
        MembershipPlan saved = plans.save(plan);
        auditPlan(AuditEventType.MEMBERSHIP_PLAN_CREATED, actor, saved);
        return response(saved);
    }

    @Transactional
    public MembershipPlanResponse update(String code, UpsertMembershipPlanRequest request,
                                         String adminEmail) {
        User actor = requireUser(adminEmail);
        String normalized = normalize(code);
        if (!normalized.equals(normalize(request.code()))) {
            throw new BadRequestException("Plan code in path and request must match");
        }
        if ("FREE".equals(normalized) && request.status() == MembershipPlanStatus.HIDDEN) {
            throw new BadRequestException("FREE plan cannot be hidden");
        }
        MembershipPlan plan = requirePlan(normalized);
        apply(plan, request);
        MembershipPlan saved = plans.save(plan);
        auditPlan(AuditEventType.MEMBERSHIP_PLAN_UPDATED, actor, saved);
        return response(saved);
    }

    @Transactional
    public MembershipPlanResponse hide(String code, String adminEmail) {
        User actor = requireUser(adminEmail);
        MembershipPlan plan = requirePlan(normalize(code));
        if ("FREE".equals(plan.getCode())) throw new BadRequestException("FREE plan cannot be hidden");
        plan.setStatus(MembershipPlanStatus.HIDDEN);
        MembershipPlan saved = plans.save(plan);
        auditPlan(AuditEventType.MEMBERSHIP_PLAN_HIDDEN, actor, saved);
        return response(saved);
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

    private User requireUser(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void auditPlan(AuditEventType type, User actor, MembershipPlan plan) {
        auditService.recordSuccess(type, actor, AuditTargetType.MEMBERSHIP_PLAN, plan.getCode(), Map.of(
                "name", plan.getName(),
                "billingCycle", plan.getBillingCycle().name(),
                "price", plan.getPrice(),
                "status", plan.getStatus().name()
        ));
    }

    private String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
