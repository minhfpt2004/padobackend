package com.oneforall.padobackend.dto;

public record MockPaymentConfirmationResponse(
        MembershipPaymentResponse payment,
        UserMembershipResponse membership
) {}
