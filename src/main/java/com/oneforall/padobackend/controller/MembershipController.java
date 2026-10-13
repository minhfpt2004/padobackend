package com.oneforall.padobackend.controller;

import com.oneforall.padobackend.dto.CreateMembershipPaymentRequest;
import com.oneforall.padobackend.dto.MembershipPaymentResponse;
import com.oneforall.padobackend.dto.MembershipPlanResponse;
import com.oneforall.padobackend.dto.MockPaymentConfirmationResponse;
import com.oneforall.padobackend.dto.UserMembershipResponse;
import com.oneforall.padobackend.service.MembershipPaymentService;
import com.oneforall.padobackend.service.MembershipPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/membership")
public class MembershipController {
    private final MembershipPlanService planService;
    private final MembershipPaymentService paymentService;

    public MembershipController(MembershipPlanService planService, MembershipPaymentService paymentService) {
        this.planService = planService;
        this.paymentService = paymentService;
    }

    @GetMapping("/plans")
    List<MembershipPlanResponse> plans() {
        return planService.activePlans();
    }

    @GetMapping("/me")
    UserMembershipResponse current(Authentication authentication) {
        return paymentService.currentMembership(authentication.getName());
    }

    @PostMapping("/payments")
    ResponseEntity<MembershipPaymentResponse> createPayment(
            @Valid @RequestBody CreateMembershipPaymentRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.create(authentication.getName(), request));
    }

    @GetMapping("/payments")
    List<MembershipPaymentResponse> paymentHistory(Authentication authentication) {
        return paymentService.history(authentication.getName());
    }

    @GetMapping("/payments/{paymentId}")
    MembershipPaymentResponse payment(@PathVariable Long paymentId, Authentication authentication) {
        return paymentService.findOne(authentication.getName(), paymentId);
    }

    @PostMapping("/payments/{paymentId}/mock-confirm")
    MockPaymentConfirmationResponse confirmMock(@PathVariable Long paymentId, Authentication authentication) {
        return paymentService.confirmMock(authentication.getName(), paymentId);
    }

    @DeleteMapping("/payments/{paymentId}")
    MembershipPaymentResponse cancel(@PathVariable Long paymentId, Authentication authentication) {
        return paymentService.cancel(authentication.getName(), paymentId);
    }
}
