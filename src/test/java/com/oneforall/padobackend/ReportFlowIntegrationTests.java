package com.oneforall.padobackend;

import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReportFlowIntegrationTests {
    private static final Pattern TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern ID = Pattern.compile("\\\"id\\\":(\\d+)");

    @Autowired
    MockMvc mvc;

    @Autowired
    AuditEventRepository auditEvents;

    @Test
    void userCreatesReportAndAdminResolvesItByHidingProduct() throws Exception {
        String prefix = "report-" + UUID.randomUUID().toString().substring(0, 8);
        String sellerEmail = prefix + "-seller@pado.test";
        String reporterEmail = prefix + "-reporter@pado.test";
        register(sellerEmail);
        register(reporterEmail);
        String sellerToken = login(sellerEmail, "StrongPassword123!");
        String reporterToken = login(reporterEmail, "StrongPassword123!");
        String adminToken = login("admin@pado.test", "AdminTestPassword123!");
        long productId = createProduct(sellerToken, "Reported jacket");

        MvcResult created = mvc.perform(post("/api/reports")
                        .header("Authorization", bearer(reporterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"PRODUCT","targetId":%d,"reason":"COUNTERFEIT",
                                 "details":"The logo and serial number do not match."}
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.targetLabel").value("Reported jacket"))
                .andReturn();
        long reportId = extractLong(ID, created.getResponse().getContentAsString());

        mvc.perform(post("/api/reports")
                        .header("Authorization", bearer(reporterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"PRODUCT","targetId":%d,"reason":"SCAM"}
                                """.formatted(productId)))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/reports/mine")
                        .header("Authorization", bearer(reporterToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reportId))
                .andExpect(jsonPath("$[0].internalNote").doesNotExist());

        mvc.perform(get("/api/admin/reports")
                        .header("Authorization", bearer(adminToken))
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(reportId));

        mvc.perform(patch("/api/admin/reports/{id}/claim", reportId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"internalNote\":\"Checking seller evidence\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        mvc.perform(patch("/api/admin/reports/{id}/resolve", reportId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolutionNote":"Listing violates authenticity policy.",
                                 "action":"HIDE_PRODUCT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionAction").value("HIDE_PRODUCT"));

        mvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isNotFound());

        assertThat(auditEvents.findAll()).extracting(event -> event.getEventType())
                .contains(AuditEventType.REPORT_CREATED, AuditEventType.REPORT_CLAIMED,
                        AuditEventType.PRODUCT_MODERATION_HIDDEN, AuditEventType.REPORT_RESOLVED);
    }

    @Test
    void reporterCannotReportOwnProductAndAdminMustClaimBeforeCompletion() throws Exception {
        String prefix = "report-guard-" + UUID.randomUUID().toString().substring(0, 8);
        String sellerEmail = prefix + "@pado.test";
        String reporterEmail = prefix + "-other@pado.test";
        register(sellerEmail);
        register(reporterEmail);
        String sellerToken = login(sellerEmail, "StrongPassword123!");
        String reporterToken = login(reporterEmail, "StrongPassword123!");
        String adminToken = login("admin@pado.test", "AdminTestPassword123!");
        long productId = createProduct(sellerToken, "Guarded product");

        mvc.perform(post("/api/reports")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody(productId)))
                .andExpect(status().isConflict());

        MvcResult created = mvc.perform(post("/api/reports")
                        .header("Authorization", bearer(reporterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody(productId)))
                .andExpect(status().isCreated())
                .andReturn();
        long reportId = extractLong(ID, created.getResponse().getContentAsString());

        mvc.perform(patch("/api/admin/reports/{id}/dismiss", reportId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolutionNote\":\"No violation found\"}"))
                .andExpect(status().isConflict());
    }

    private void register(String email) throws Exception {
        AuthTestHelper.registerAndVerify(mvc, email, "StrongPassword123!");
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return extract(TOKEN, result.getResponse().getContentAsString());
    }

    private long createProduct(String token, String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","brand":"Pado","category":"Jacket","size":"M",
                                 "color":"Blue","condition":"Like new","price":320000,
                                 "shippingFee":15000,"description":"Report integration product",
                                 "sellerLocation":"District 1","sold":false,
                                 "images":["https://example.test/report.jpg"],"deliveryOptions":["SHIP"]}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractLong(ID, result.getResponse().getContentAsString());
    }

    private String reportBody(long productId) {
        return "{\"targetType\":\"PRODUCT\",\"targetId\":" + productId
                + ",\"reason\":\"MISLEADING_INFORMATION\"}";
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
