package com.oneforall.padobackend;

import com.oneforall.padobackend.audit.AuditRequestFilter;
import com.oneforall.padobackend.entity.AuditEvent;
import com.oneforall.padobackend.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAuditApiIntegrationTests {
    private static final Pattern TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern ID = Pattern.compile("\\\"id\\\":(\\d+)");

    @Autowired
    MockMvc mvc;

    @Autowired
    AuditEventRepository auditEvents;

    @Test
    void adminCanFilterPageAndInspectAuditEventsAndCustomerTimeline() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String requestId = "phase5-product-" + suffix;
        String email = "phase5-" + suffix + "@pado.test";
        String password = "StrongPassword123!";
        long userId = extractLong(ID, AuthTestHelper.registerAndVerify(mvc, email, password));
        String userToken = login(email, password);

        mvc.perform(post("/api/products")
                        .header("Authorization", bearer(userToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody()))
                .andExpect(status().isCreated());

        AuditEvent productEvent = auditEvents.findByRequestIdOrderByIdAsc(requestId).getFirst();
        String adminToken = login("admin@pado.test", "AdminTestPassword123!");

        mvc.perform(get("/api/admin/audit-events"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/audit-events").header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit-events/export").header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/audit-events")
                        .header("Authorization", bearer(adminToken))
                        .param("actorUserId", String.valueOf(userId))
                        .param("subjectUserId", String.valueOf(userId))
                        .param("actorEmail", email.toUpperCase())
                        .param("category", "PRODUCT")
                        .param("eventType", "PRODUCT_CREATED")
                        .param("outcome", "SUCCESS")
                        .param("source", "USER")
                        .param("targetType", "PRODUCT")
                        .param("requestId", requestId)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(productEvent.getId()))
                .andExpect(jsonPath("$.content[0].actorUserId").value(userId))
                .andExpect(jsonPath("$.content[0].subjectUserId").value(userId))
                .andExpect(jsonPath("$.content[0].actorEmail").value(email))
                .andExpect(jsonPath("$.content[0].requestId").value(requestId))
                .andExpect(jsonPath("$.content[0].metadata.requestPath").value("/api/products"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        String exportRequestId = "phase5-export-" + suffix;
        mvc.perform(get("/api/admin/audit-events/export")
                        .header("Authorization", bearer(adminToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, exportRequestId)
                        .param("requestId", requestId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", containsString("pado-audit-")))
                .andExpect(header().string("X-Audit-Export-Rows", "1"))
                .andExpect(header().string("X-Audit-Export-Truncated", "false"))
                .andExpect(content().string(containsString("PRODUCT_CREATED")))
                .andExpect(content().string(containsString(email)));
        AuditEvent exportEvent = auditEvents.findByRequestIdOrderByIdAsc(exportRequestId).getFirst();
        org.assertj.core.api.Assertions.assertThat(exportEvent.getEventType())
                .isEqualTo(com.oneforall.padobackend.entity.AuditEventType.AUDIT_EXPORTED);
        org.assertj.core.api.Assertions.assertThat(exportEvent.getActorEmail())
                .isEqualToIgnoringCase("admin@pado.test");

        mvc.perform(get("/api/admin/audit-events/{id}", productEvent.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("PRODUCT_CREATED"))
                .andExpect(jsonPath("$.targetId").value(productEvent.getTargetId()));

        mvc.perform(get("/api/admin/users/{id}/audit-events", userId)
                        .header("Authorization", bearer(adminToken))
                        .param("category", "PRODUCT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("PRODUCT_CREATED"));

        long paymentId = createPayment(userToken);
        mvc.perform(post("/api/admin/membership/payments/{id}/confirm", paymentId)
                        .header("Authorization", bearer(adminToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, "phase5-admin-confirm-" + suffix))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/users/{id}/audit-events", userId)
                        .header("Authorization", bearer(adminToken))
                        .param("eventType", "MEMBERSHIP_ACTIVATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].actorEmail").value("admin@pado.test"))
                .andExpect(jsonPath("$.content[0].targetType").value("USER"))
                .andExpect(jsonPath("$.content[0].targetId").value(String.valueOf(userId)));

        mvc.perform(get("/api/admin/audit-events/filter-options")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", hasItem("AUTHENTICATION")))
                .andExpect(jsonPath("$.eventTypes", hasItem("ORDER_CREATED")))
                .andExpect(jsonPath("$.outcomes", hasItem("FAILURE")))
                .andExpect(jsonPath("$.sources", hasItem("ADMIN")))
                .andExpect(jsonPath("$.targetTypes", hasItem("MEMBERSHIP_PAYMENT")));
    }

    @Test
    void invalidFiltersAndUnknownAuditEventReturnClientErrors() throws Exception {
        String adminToken = login("admin@pado.test", "AdminTestPassword123!");

        mvc.perform(get("/api/admin/audit-events")
                        .header("Authorization", bearer(adminToken))
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/audit-events")
                        .header("Authorization", bearer(adminToken))
                        .param("eventType", "NOT_AN_EVENT"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/audit-events")
                        .header("Authorization", bearer(adminToken))
                        .param("from", Instant.parse("2026-12-01T00:00:00Z").toString())
                        .param("to", Instant.parse("2026-11-01T00:00:00Z").toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/audit-events/{id}", Long.MAX_VALUE)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return extract(TOKEN, result.getResponse().getContentAsString());
    }

    private long createPayment(String token) throws Exception {
        MvcResult result = mvc.perform(post("/api/membership/payments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"PLUS\",\"paymentMethod\":\"QR\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractLong(ID, result.getResponse().getContentAsString());
    }

    private String productBody() {
        return """
                {"title":"Phase 5 audit product","brand":"Pado","category":"Jacket","size":"M",
                 "color":"Blue","condition":"Like new","price":320000,"originalPrice":900000,
                 "shippingFee":15000,"description":"Admin audit query integration product",
                 "sellerLocation":"District 1, HCMC","sold":false,
                 "images":["https://example.test/phase5.jpg"],"deliveryOptions":["SHIP"]}
                """;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String extract(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) throw new AssertionError("Expected value not found in response: " + body);
        return matcher.group(1);
    }

    private long extractLong(Pattern pattern, String body) {
        return Long.parseLong(extract(pattern, body));
    }
}
