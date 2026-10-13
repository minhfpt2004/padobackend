package com.oneforall.padobackend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MembershipFlowIntegrationTests {
    private static final Pattern TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern ID = Pattern.compile("\\\"id\\\":(\\d+)");

    @Autowired
    MockMvc mvc;

    @Test
    void mockPaymentPersistsAndActivatesMembershipOrBoostExactlyOnce() throws Exception {
        mvc.perform(get("/api/membership/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("FREE")))
                .andExpect(jsonPath("$[*].code", hasItem("PLUS")))
                .andExpect(jsonPath("$[*].code", hasItem("PRO")))
                .andExpect(jsonPath("$[*].code", hasItem("BOOST")));

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "membership-" + suffix + "@pado.test";
        String password = "StrongPassword123!";
        register(email, password);
        String token = login(email, password);

        mvc.perform(get("/api/membership/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("FREE"))
                .andExpect(jsonPath("$.boostBalance").value(0));

        mvc.perform(post("/api/membership/payments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"FREE\",\"paymentMethod\":\"QR\"}"))
                .andExpect(status().isBadRequest());

        long boostPaymentId = createPayment(token, "BOOST", "MOMO");
        mvc.perform(post("/api/membership/payments/{id}/mock-confirm", boostPaymentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.payment.mockPayment").value(true))
                .andExpect(jsonPath("$.membership.planCode").value("FREE"))
                .andExpect(jsonPath("$.membership.boostBalance").value(5));

        mvc.perform(post("/api/membership/payments/{id}/mock-confirm", boostPaymentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membership.boostBalance").value(5));

        long plusPaymentId = createPayment(token, "PLUS", "QR");
        mvc.perform(post("/api/membership/payments/{id}/mock-confirm", plusPaymentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membership.planCode").value("PLUS"))
                .andExpect(jsonPath("$.membership.boostBalance").value(10))
                .andExpect(jsonPath("$.membership.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.membership.expiresAt").isNotEmpty());

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipPlanCode").value("PLUS"))
                .andExpect(jsonPath("$.boostBalance").value(10));

        mvc.perform(get("/api/membership/payments").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].status", hasItem("PAID")));

        long cancelledId = createPayment(token, "PRO", "QR");
        mvc.perform(delete("/api/membership/payments/{id}", cancelledId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/membership/payments/{id}/mock-confirm", cancelledId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());

        long adminConfirmedId = createPayment(token, "PRO", "QR");
        String adminToken = login("admin@pado.test", "AdminTestPassword123!");
        mvc.perform(post("/api/admin/membership/payments/{id}/confirm", adminConfirmedId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.membership.planCode").value("PRO"));
    }

    private long createPayment(String token, String planCode, String method) throws Exception {
        ResultActions action = mvc.perform(post("/api/membership/payments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"" + planCode + "\",\"paymentMethod\":\"" + method + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        if ("QR".equals(method)) {
            action.andExpect(jsonPath("$.qrImageUrl").value(org.hamcrest.Matchers.startsWith("https://img.vietqr.io/image/970436-1025835104-compact2.png")))
                    .andExpect(jsonPath("$.transferContent").value(org.hamcrest.Matchers.startsWith("PADO")))
                    .andExpect(jsonPath("$.bankName").value("Vietcombank"));
        }
        MvcResult result = action.andReturn();
        Matcher matcher = ID.matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) throw new AssertionError("Payment id not found");
        return Long.parseLong(matcher.group(1));
    }

    private void register(String email, String password) throws Exception {
        AuthTestHelper.registerAndVerify(mvc, email, password);
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        Matcher matcher = TOKEN.matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) throw new AssertionError("Access token not found");
        return matcher.group(1);
    }

    private String authBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
