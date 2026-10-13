package com.oneforall.padobackend.controller;

import com.oneforall.padobackend.dto.*;
import com.oneforall.padobackend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    ResponseEntity<RegistrationStartedResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.register(request));
    }

    @PostMapping("/register/verify")
    ResponseEntity<AuthResponse> verifyRegistration(@Valid @RequestBody VerifyRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.verifyRegistration(request));
    }

    @PostMapping("/register/resend")
    RegistrationStartedResponse resendRegistrationOtp(@Valid @RequestBody ResendRegistrationOtpRequest request) {
        return authService.resendRegistrationOtp(request);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    MessageResponse logout() {
        return new MessageResponse("Logged out. Delete the access token on the client; the old token remains valid until it expires.");
    }

    @GetMapping("/me")
    UserResponse me(Authentication authentication) {
        return authService.currentUser(authentication.getName());
    }
}
