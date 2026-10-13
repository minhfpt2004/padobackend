package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.MembershipPayment;
import com.oneforall.padobackend.entity.PaymentMethod;
import com.oneforall.padobackend.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record MembershipPaymentResponse(
        Long id,
        String paymentCode,
        Long userId,
        String userEmail,
        String planCode,
        String planName,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        boolean mockPayment,
        String qrImageUrl,
        String transferContent,
        String bankName,
        String bankAccountNumber,
        String bankAccountName,
        Instant createdAt,
        Instant paidAt
) {
    public static MembershipPaymentResponse from(MembershipPayment payment, String qrImageUrl,
                                                  String transferContent, String bankName,
                                                  String bankAccountNumber, String bankAccountName) {
        return new MembershipPaymentResponse(payment.getId(), payment.getPaymentCode(), payment.getUserId(),
                payment.getUserEmail(), payment.getPlanCode(), payment.getPlanName(), payment.getAmount(),
                payment.getPaymentMethod(), payment.getStatus(), payment.isMockPayment(),
                qrImageUrl, transferContent, bankName, bankAccountNumber, bankAccountName,
                payment.getCreatedAt(), payment.getPaidAt());
    }
}
