package com.oneforall.padobackend.service;

import com.oneforall.padobackend.dto.*;
import com.oneforall.padobackend.entity.PendingRegistration;
import com.oneforall.padobackend.entity.Role;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditOutcome;
import com.oneforall.padobackend.entity.AuditSource;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.entity.UserModerationStatus;
import com.oneforall.padobackend.exception.BadRequestException;
import com.oneforall.padobackend.exception.ConflictException;
import com.oneforall.padobackend.exception.ResourceNotFoundException;
import com.oneforall.padobackend.repository.PendingRegistrationRepository;
import com.oneforall.padobackend.repository.UserRepository;
import com.oneforall.padobackend.repository.MembershipPlanRepository;
import com.oneforall.padobackend.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MembershipPlanRepository membershipPlans;
    private final PendingRegistrationRepository pendingRegistrations;
    private final RegistrationEmailService registrationEmails;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long otpExpirationMinutes;
    private final long resendCooldownSeconds;
    private final int maxAttempts;
    private final String fixedOtp;
    private final boolean exposeDevelopmentOtp;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       MembershipPlanRepository membershipPlans,
                       PendingRegistrationRepository pendingRegistrations,
                       RegistrationEmailService registrationEmails,
                       AuditService auditService,
                       @Value("${app.auth.otp.expiration-minutes:5}") long otpExpirationMinutes,
                       @Value("${app.auth.otp.resend-cooldown-seconds:60}") long resendCooldownSeconds,
                       @Value("${app.auth.otp.max-attempts:5}") int maxAttempts,
                       @Value("${app.auth.otp.fixed-code:}") String fixedOtp,
                       @Value("${app.auth.otp.expose-code:false}") boolean exposeDevelopmentOtp) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.membershipPlans = membershipPlans;
        this.pendingRegistrations = pendingRegistrations;
        this.registrationEmails = registrationEmails;
        this.auditService = auditService;
        this.otpExpirationMinutes = otpExpirationMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxAttempts = maxAttempts;
        this.fixedOtp = fixedOtp.trim();
        this.exposeDevelopmentOtp = exposeDevelopmentOtp;
    }

    @Transactional
    public RegistrationStartedResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        try {
            if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("Email is already registered");

            Instant now = Instant.now();
            PendingRegistration pending = pendingRegistrations.findLockedByEmail(email).orElseGet(PendingRegistration::new);
            if (pending.getEmail() != null && pending.getResendAvailableAt().isAfter(now)) {
                throw new BadRequestException("Please wait before requesting another OTP");
            }

            String otp = generateOtp();
            pending.setEmail(email);
            pending.setPasswordHash(passwordEncoder.encode(request.password()));
            updateOtp(pending, otp, now);
            pendingRegistrations.save(pending);
            String deliveryMode = registrationEmails.sendRegistrationOtp(email, otp, otpExpirationMinutes);
            RegistrationStartedResponse response = startedResponse(pending, deliveryMode, otp, now);
            auditSuccess(AuditEventType.REGISTRATION_STARTED, AuditSource.ANONYMOUS, null, email,
                    Map.of("deliveryMode", deliveryMode, "expiresInSeconds", response.expiresInSeconds()));
            return response;
        } catch (RuntimeException exception) {
            auditFailure(AuditEventType.REGISTRATION_STARTED, email, exception);
            throw exception;
        }
    }

    @Transactional
    public RegistrationStartedResponse resendRegistrationOtp(ResendRegistrationOtpRequest request) {
        String email = normalize(request.email());
        try {
            if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("Email is already registered");

            Instant now = Instant.now();
            PendingRegistration pending = pendingRegistrations.findLockedByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("No pending registration found for this email"));
            if (pending.getResendAvailableAt().isAfter(now)) {
                throw new BadRequestException("Please wait before requesting another OTP");
            }

            String otp = generateOtp();
            updateOtp(pending, otp, now);
            pendingRegistrations.save(pending);
            String deliveryMode = registrationEmails.sendRegistrationOtp(email, otp, otpExpirationMinutes);
            RegistrationStartedResponse response = startedResponse(pending, deliveryMode, otp, now);
            auditSuccess(AuditEventType.OTP_RESENT, AuditSource.ANONYMOUS, null, email,
                    Map.of("deliveryMode", deliveryMode, "expiresInSeconds", response.expiresInSeconds()));
            return response;
        } catch (RuntimeException exception) {
            auditFailure(AuditEventType.OTP_RESENT, email, exception);
            throw exception;
        }
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public AuthResponse verifyRegistration(VerifyRegistrationRequest request) {
        String email = normalize(request.email());
        try {
            if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("Email is already registered");

            PendingRegistration pending = pendingRegistrations.findLockedByEmail(email)
                    .orElseThrow(() -> new BadRequestException("OTP is invalid or has expired"));
            Instant now = Instant.now();
            if (!pending.getExpiresAt().isAfter(now)) {
                pendingRegistrations.delete(pending);
                throw new BadRequestException("OTP is invalid or has expired");
            }
            if (pending.getAttempts() >= maxAttempts) {
                throw new BadRequestException("Too many invalid OTP attempts. Request a new code.");
            }
            if (!passwordEncoder.matches(request.otp(), pending.getOtpHash())) {
                pending.setAttempts(pending.getAttempts() + 1);
                pendingRegistrations.save(pending);
                throw new BadRequestException("OTP is invalid or has expired");
            }

            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(pending.getPasswordHash());
            user.setRole(Role.USER);
            user.setMembershipPlan(membershipPlans.findById("FREE").orElse(null));
            User saved = users.save(user);
            pendingRegistrations.delete(pending);
            auditSuccess(AuditEventType.REGISTRATION_VERIFIED, AuditSource.USER, saved, email,
                    Map.of("membershipPlan", "FREE"));
            return authResponse(saved);
        } catch (BadRequestException exception) {
            auditFailure(AuditEventType.OTP_VERIFICATION_FAILED, email, exception);
            throw exception;
        } catch (RuntimeException exception) {
            auditFailure(AuditEventType.REGISTRATION_VERIFIED, email, exception);
            throw exception;
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        try {
            User user = users.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new BadCredentialsException("Invalid credentials");
            }
            if (user.getModerationStatus() == UserModerationStatus.SUSPENDED
                    || user.getModerationStatus() == UserModerationStatus.DEACTIVATED) {
                throw new BadCredentialsException("Account is unavailable");
            }
            auditSuccess(AuditEventType.LOGIN_SUCCEEDED, source(user), user, email, Map.of());
            return authResponse(user);
        } catch (BadCredentialsException exception) {
            auditFailure(AuditEventType.LOGIN_FAILED, email, exception);
            throw exception;
        }
    }

    @Transactional
    public void logout(String email) {
        User user = users.findByEmailIgnoreCase(normalize(email))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        auditSuccess(AuditEventType.LOGOUT, source(user), user, user.getEmail(), Map.of());
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String email) {
        return UserResponse.from(users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void updateOtp(PendingRegistration pending, String otp, Instant now) {
        pending.setOtpHash(passwordEncoder.encode(otp));
        pending.setAttempts(0);
        pending.setExpiresAt(now.plus(Duration.ofMinutes(otpExpirationMinutes)));
        pending.setResendAvailableAt(now.plusSeconds(resendCooldownSeconds));
    }

    private RegistrationStartedResponse startedResponse(PendingRegistration pending, String deliveryMode,
                                                        String otp, Instant now) {
        return new RegistrationStartedResponse(
                pending.getEmail(),
                Math.max(0, Duration.between(now, pending.getExpiresAt()).toSeconds()),
                Math.max(0, Duration.between(now, pending.getResendAvailableAt()).toSeconds()),
                deliveryMode,
                exposeDevelopmentOtp && "LOCAL_LOG".equals(deliveryMode) ? otp : null
        );
    }

    private AuthResponse authResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer",
                jwtService.getExpirationSeconds(), UserResponse.from(user));
    }

    private String generateOtp() {
        if (!fixedOtp.isBlank()) {
            if (!fixedOtp.matches("\\d{6}")) throw new IllegalStateException("AUTH_OTP_FIXED_CODE must contain 6 digits");
            return fixedOtp;
        }
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }

    private void auditSuccess(AuditEventType type, AuditSource source, User user,
                              String email, Map<String, Object> metadata) {
        auditService.record(auditCommand(type, AuditOutcome.SUCCESS, source, user, email, metadata, null));
    }

    private void auditFailure(AuditEventType type, String email, RuntimeException exception) {
        try {
            auditService.recordIsolated(auditCommand(type, AuditOutcome.FAILURE, AuditSource.ANONYMOUS,
                    null, email, Map.of(), errorCode(exception)));
        } catch (RuntimeException auditException) {
            log.warn("Could not persist {} audit event for {}", type, email, auditException);
        }
    }

    private AuditEventCommand auditCommand(AuditEventType type, AuditOutcome outcome, AuditSource source,
                                           User user, String email, Map<String, Object> metadata,
                                           String errorCode) {
        return new AuditEventCommand(
                type,
                outcome,
                source,
                user == null ? null : user.getId(),
                email,
                user == null ? null : user.getRole(),
                AuditTargetType.USER,
                user == null || user.getId() == null ? null : user.getId().toString(),
                null,
                null,
                null,
                metadata,
                errorCode
        );
    }

    private AuditSource source(User user) {
        return user.getRole() == Role.ADMIN ? AuditSource.ADMIN : AuditSource.USER;
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof BadCredentialsException) return "INVALID_CREDENTIALS";
        if (exception instanceof ConflictException) return "CONFLICT";
        if (exception instanceof BadRequestException) return "BAD_REQUEST";
        if (exception instanceof ResourceNotFoundException) return "NOT_FOUND";
        if (exception instanceof com.oneforall.padobackend.exception.EmailDeliveryException) {
            return "EMAIL_DELIVERY_FAILED";
        }
        return "AUTHENTICATION_ERROR";
    }
}
