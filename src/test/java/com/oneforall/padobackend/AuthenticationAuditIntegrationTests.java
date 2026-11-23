package com.oneforall.padobackend;

import com.oneforall.padobackend.audit.AuditRequestFilter;
import com.oneforall.padobackend.entity.AuditEvent;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditOutcome;
import com.oneforall.padobackend.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationAuditIntegrationTests {
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuditEventRepository auditEvents;

    @Test
    void registrationOtpLoginAndLogoutCreateCorrelatedAuditEvents() throws Exception {
        String email = "audit-auth-" + UUID.randomUUID().toString().substring(0, 8) + "@pado.test";
        String password = "StrongPassword123!";

        mvc.perform(post("/api/auth/register")
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, "audit-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(email, password)))
                .andExpect(status().isAccepted());

        mvc.perform(post("/api/auth/register/verify")
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, "audit-otp-failed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otpBody(email, "000000")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/register/resend")
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, "audit-otp-resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/register/verify")
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, "audit-otp-success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otpBody(email, "123456")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login")
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, "audit-login-failure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(email, "WrongPassword123!")))
                .andExpect(status().isUnauthorized());

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, "audit-login-success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        mvc.perform(post("/api/auth/logout")
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, "audit-logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(login)))
                .andExpect(status().isOk());

        List<AuditEvent> events = auditEvents.findByActorEmailIgnoreCaseOrderByCreatedAtAsc(email);
        assertThat(events).hasSize(7);
        assertThat(events).extracting(AuditEvent::getEventType).containsExactly(
                AuditEventType.REGISTRATION_STARTED,
                AuditEventType.OTP_VERIFICATION_FAILED,
                AuditEventType.OTP_RESENT,
                AuditEventType.REGISTRATION_VERIFIED,
                AuditEventType.LOGIN_FAILED,
                AuditEventType.LOGIN_SUCCEEDED,
                AuditEventType.LOGOUT
        );

        AuditEvent failedLogin = event(events, AuditEventType.LOGIN_FAILED);
        assertThat(failedLogin.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(failedLogin.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(failedLogin.getRequestId()).isEqualTo("audit-login-failure");
        assertThat(failedLogin.getMetadata()).containsEntry("requestPath", "/api/auth/login");
        assertThat(failedLogin.getActorUserId()).isNull();

        AuditEvent successfulLogin = event(events, AuditEventType.LOGIN_SUCCEEDED);
        assertThat(successfulLogin.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(successfulLogin.getActorUserId()).isNotNull();
        assertThat(successfulLogin.getTargetId()).isEqualTo(successfulLogin.getActorUserId().toString());
        assertThat(event(events, AuditEventType.LOGOUT).getRequestId()).isEqualTo("audit-logout");

        String serializedMetadata = events.stream().map(AuditEvent::getMetadata).toList().toString().toLowerCase();
        assertThat(serializedMetadata)
                .doesNotContain(password.toLowerCase())
                .doesNotContain("000000")
                .doesNotContain(accessToken(login).toLowerCase());
    }

    private AuditEvent event(List<AuditEvent> events, AuditEventType type) {
        return events.stream().filter(event -> event.getEventType() == type).findFirst().orElseThrow();
    }

    private String accessToken(MvcResult result) throws Exception {
        Matcher matcher = ACCESS_TOKEN.matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) throw new AssertionError("Access token not found in login response");
        return matcher.group(1);
    }

    private String authBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String otpBody(String email, String otp) {
        return "{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\"}";
    }
}
