package com.oneforall.padobackend.security;

import com.oneforall.padobackend.entity.Role;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.repository.UserRepository;
import com.oneforall.padobackend.repository.MembershipPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class AdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final MembershipPlanRepository membershipPlans;

    public AdminInitializer(UserRepository users, PasswordEncoder passwordEncoder,
                            MembershipPlanRepository membershipPlans,
                            @Value("${app.admin.email:}") String email,
                            @Value("${app.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.membershipPlans = membershipPlans;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.length() < 12) {
            throw new IllegalStateException("ADMIN_EMAIL and ADMIN_PASSWORD (minimum 12 characters) must both be valid");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            log.info("Admin bootstrap skipped because account already exists");
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setMembershipPlan(membershipPlans.findById("FREE").orElseThrow());
        users.save(admin);
        log.info("Initial admin account created from environment configuration");
    }
}
