package com.oneforall.padobackend.controller;

import com.oneforall.padobackend.dto.MembershipPaymentResponse;
import com.oneforall.padobackend.dto.MembershipPlanResponse;
import com.oneforall.padobackend.dto.MockPaymentConfirmationResponse;
import com.oneforall.padobackend.dto.UpsertMembershipPlanRequest;
import com.oneforall.padobackend.service.MembershipPaymentService;
import com.oneforall.padobackend.service.MembershipPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/membership")
public class AdminMembershipController {
    private final MembershipPlanService planService;
    private final MembershipPaymentService paymentService;

    public AdminMembershipController(MembershipPlanService planService, MembershipPaymentService paymentService) {
        this.planService = planService;
        this.paymentService = paymentService;
    }

    @GetMapping("/plans")
    List<MembershipPlanResponse> plans() {
        return planService.allPlans();
    }

    @PostMapping("/plans")
    ResponseEntity<MembershipPlanResponse> create(@Valid @RequestBody UpsertMembershipPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.create(request));
    }

    @PutMapping("/plans/{code}")
    MembershipPlanResponse update(@PathVariable String code,
                                  @Valid @RequestBody UpsertMembershipPlanRequest request) {
        return planService.update(code, request);
    }

    @DeleteMapping("/plans/{code}")
    MembershipPlanResponse hide(@PathVariable String code) {
        return planService.hide(code);
    }

    @GetMapping("/payments")
    List<MembershipPaymentResponse> payments() {
        return paymentService.allPayments();
    }

    @PostMapping("/payments/{paymentId}/confirm")
    MockPaymentConfirmationResponse confirmPayment(@PathVariable Long paymentId) {
        return paymentService.confirmByAdmin(paymentId);
    }
}
