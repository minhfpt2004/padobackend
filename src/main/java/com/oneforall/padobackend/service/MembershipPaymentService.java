package com.oneforall.padobackend.service;

import com.oneforall.padobackend.dto.CreateMembershipPaymentRequest;
import com.oneforall.padobackend.dto.MembershipPaymentResponse;
import com.oneforall.padobackend.dto.MockPaymentConfirmationResponse;
import com.oneforall.padobackend.dto.UserMembershipResponse;
import com.oneforall.padobackend.entity.BillingCycle;
import com.oneforall.padobackend.entity.MembershipPayment;
import com.oneforall.padobackend.entity.MembershipPlan;
import com.oneforall.padobackend.entity.MembershipPlanStatus;
import com.oneforall.padobackend.entity.PaymentStatus;
import com.oneforall.padobackend.entity.PaymentMethod;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.exception.BadRequestException;
import com.oneforall.padobackend.exception.ConflictException;
import com.oneforall.padobackend.exception.ResourceNotFoundException;
import com.oneforall.padobackend.repository.MembershipPaymentRepository;
import com.oneforall.padobackend.repository.MembershipPlanRepository;
import com.oneforall.padobackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MembershipPaymentService {
    private final MembershipPlanRepository plans;
    private final MembershipPaymentRepository payments;
    private final UserRepository users;
    private final boolean mockPaymentEnabled;
    private final String vietQrBankId;
    private final String vietQrBankName;
    private final String vietQrAccountNumber;
    private final String vietQrAccountName;
    private final String vietQrTemplate;

    public MembershipPaymentService(MembershipPlanRepository plans, MembershipPaymentRepository payments,
                                    UserRepository users,
                                    @Value("${app.payment.mock-enabled:true}") boolean mockPaymentEnabled,
                                    @Value("${app.payment.vietqr.bank-id:}") String vietQrBankId,
                                    @Value("${app.payment.vietqr.bank-name:}") String vietQrBankName,
                                    @Value("${app.payment.vietqr.account-number:}") String vietQrAccountNumber,
                                    @Value("${app.payment.vietqr.account-name:}") String vietQrAccountName,
                                    @Value("${app.payment.vietqr.template:compact2}") String vietQrTemplate) {
        this.plans = plans;
        this.payments = payments;
        this.users = users;
        this.mockPaymentEnabled = mockPaymentEnabled;
        this.vietQrBankId = vietQrBankId.trim();
        this.vietQrBankName = vietQrBankName.trim();
        this.vietQrAccountNumber = vietQrAccountNumber.trim();
        this.vietQrAccountName = vietQrAccountName.trim();
        this.vietQrTemplate = vietQrTemplate.trim();
    }

    @Transactional(readOnly = true)
    public UserMembershipResponse currentMembership(String email) {
        return UserMembershipResponse.from(requireUser(email));
    }

    @Transactional
    public MembershipPaymentResponse create(String email, CreateMembershipPaymentRequest request) {
        User user = requireUser(email);
        String code = request.planCode().trim().toUpperCase(Locale.ROOT);
        MembershipPlan plan = plans.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found"));
        if (plan.getStatus() != MembershipPlanStatus.ACTIVE) {
            throw new BadRequestException("Membership plan is not available");
        }
        if (plan.getPrice().signum() <= 0 || plan.getBillingCycle() == BillingCycle.LIFETIME) {
            throw new BadRequestException("This membership plan does not require payment");
        }
        if (!mockPaymentEnabled && request.paymentMethod() != PaymentMethod.QR) {
            throw new BadRequestException("Only VietQR payment is currently available");
        }
        if (request.paymentMethod() == PaymentMethod.QR) requireVietQrConfigured();

        MembershipPayment payment = new MembershipPayment();
        payment.setPaymentCode("PADO" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT));
        payment.setUserId(user.getId());
        payment.setUserEmail(user.getEmail());
        payment.setPlanCode(plan.getCode());
        payment.setPlanName(plan.getName());
        payment.setAmount(plan.getPrice());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMockPayment(mockPaymentEnabled);
        return response(payments.save(payment));
    }

    @Transactional(readOnly = true)
    public List<MembershipPaymentResponse> history(String email) {
        User user = requireUser(email);
        return payments.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public MembershipPaymentResponse findOne(String email, Long paymentId) {
        User user = requireUser(email);
        return payments.findById(paymentId)
                .filter(payment -> payment.getUserId().equals(user.getId()))
                .map(this::response)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    @Transactional
    public MockPaymentConfirmationResponse confirmMock(String email, Long paymentId) {
        if (!mockPaymentEnabled) throw new ConflictException("Mock payment confirmation is disabled");
        User user = requireUser(email);
        MembershipPayment payment = payments.findLockedByIdAndUserId(paymentId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new ConflictException("Cancelled payment cannot be confirmed");
        }
        if (payment.getStatus() == PaymentStatus.PENDING) {
            MembershipPlan plan = plans.findById(payment.getPlanCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found"));
            activate(user, plan);
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(Instant.now());
            users.save(user);
            payments.save(payment);
        }
        return new MockPaymentConfirmationResponse(response(payment),
                UserMembershipResponse.from(user));
    }

    @Transactional
    public MockPaymentConfirmationResponse confirmByAdmin(Long paymentId) {
        MembershipPayment payment = payments.findLockedById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new ConflictException("Cancelled payment cannot be confirmed");
        }
        User user = users.findById(payment.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (payment.getStatus() == PaymentStatus.PENDING) {
            MembershipPlan plan = plans.findById(payment.getPlanCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found"));
            activate(user, plan);
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(Instant.now());
            users.save(user);
            payments.save(payment);
        }
        return new MockPaymentConfirmationResponse(response(payment), UserMembershipResponse.from(user));
    }

    @Transactional
    public MembershipPaymentResponse cancel(String email, Long paymentId) {
        User user = requireUser(email);
        MembershipPayment payment = payments.findLockedByIdAndUserId(paymentId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new ConflictException("Paid payment cannot be cancelled");
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        return response(payments.save(payment));
    }

    @Transactional(readOnly = true)
    public List<MembershipPaymentResponse> allPayments() {
        return payments.findAllByOrderByCreatedAtDesc().stream()
                .map(this::response).toList();
    }

    private MembershipPaymentResponse response(MembershipPayment payment) {
        if (payment.getPaymentMethod() != PaymentMethod.QR) {
            return MembershipPaymentResponse.from(payment, null, null, null, null, null);
        }
        String content = payment.getPaymentCode().replaceAll("[^A-Za-z0-9]", "");
        String amount = payment.getAmount().stripTrailingZeros().toPlainString();
        String qrUrl = "https://img.vietqr.io/image/" + encodePath(vietQrBankId) + "-"
                + encodePath(vietQrAccountNumber) + "-" + encodePath(vietQrTemplate)
                + ".png?amount=" + encode(amount)
                + "&addInfo=" + encode(content)
                + "&accountName=" + encode(vietQrAccountName);
        return MembershipPaymentResponse.from(payment, qrUrl, content, vietQrBankName,
                vietQrAccountNumber, vietQrAccountName);
    }

    private void requireVietQrConfigured() {
        if (!vietQrBankId.matches("\\d{6}") || !vietQrAccountNumber.matches("[A-Za-z0-9]{6,19}")
                || vietQrAccountName.isBlank() || vietQrTemplate.isBlank()) {
            throw new IllegalStateException("VietQR receiving account is not configured correctly");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String encodePath(String value) {
        return value.replaceAll("[^A-Za-z0-9_]", "");
    }

    private void activate(User user, MembershipPlan plan) {
        int nextBalance = Math.addExact(user.getBoostBalance(), plan.getBoostQuota());
        user.setBoostBalance(nextBalance);
        if (plan.getBillingCycle() == BillingCycle.PER_USE) return;

        Instant now = Instant.now();
        user.setMembershipPlan(plan);
        user.setMembershipStartedAt(now);
        user.setMembershipExpiresAt(switch (plan.getBillingCycle()) {
            case MONTHLY -> now.plus(30, ChronoUnit.DAYS);
            case YEARLY -> now.plus(365, ChronoUnit.DAYS);
            case LIFETIME, PER_USE -> null;
        });
    }

    private User requireUser(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
