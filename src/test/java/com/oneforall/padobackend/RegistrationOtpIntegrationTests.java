package com.oneforall.padobackend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RegistrationOtpIntegrationTests {
    @Autowired
    MockMvc mvc;

    @Test
    void accountIsCreatedOnlyAfterEmailOtpIsVerified() throws Exception {
        String email = "otp-" + UUID.randomUUID().toString().substring(0, 8) + "@pado.test";
        String password = "StrongPassword123!";

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(email, password)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.deliveryMode").value("LOCAL_LOG"))
                .andExpect(jsonPath("$.developmentOtp").value("123456"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(password))));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(email, password)))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otpBody(email, "000000")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/register/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.developmentOtp").value("123456"));

        mvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otpBody(email, "123456")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("USER"));

        mvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otpBody(email, "123456")))
                .andExpect(status().isConflict());
    }

    private String authBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String otpBody(String email, String otp) {
        return "{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\"}";
    }
}
