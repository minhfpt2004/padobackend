package com.oneforall.padobackend;

import com.oneforall.padobackend.audit.AuditRequestFilter;
import com.oneforall.padobackend.entity.AuditEvent;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditSource;
import com.oneforall.padobackend.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BusinessAuditIntegrationTests {
    private static final Pattern TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern ID = Pattern.compile("\\\"id\\\":(\\d+)");

    @Autowired
    MockMvc mvc;

    @Autowired
    AuditEventRepository auditEvents;

    @Test
    void customerMutationsCreateCorrelatedAuditEvents() throws Exception {
        String prefix = "business-" + UUID.randomUUID().toString().substring(0, 8);
        String sellerEmail = prefix + "-seller@pado.test";
        String buyerEmail = prefix + "-buyer@pado.test";
        String password = "StrongPassword123!";
        long sellerId = register(sellerEmail, password);
        long buyerId = register(buyerEmail, password);
        String sellerToken = login(sellerEmail, password);
        String buyerToken = login(buyerEmail, password);

        long productId = createProduct(sellerToken, prefix + "-product-create", "Audit jacket");
        assertEvents(prefix + "-product-create", sellerEmail, AuditSource.USER,
                AuditEventType.PRODUCT_CREATED);

        mvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", bearer(sellerToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, prefix + "-product-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("Updated audit jacket")))
                .andExpect(status().isOk());
        assertEvents(prefix + "-product-update", sellerEmail, AuditSource.USER,
                AuditEventType.PRODUCT_UPDATED);

        long orderId = createOrder(buyerToken, productId, prefix + "-order-create");
        assertEvents(prefix + "-order-create", buyerEmail, AuditSource.USER,
                AuditEventType.ORDER_CREATED);

        updateOrderStatus(sellerToken, orderId, "CONFIRMED", prefix + "-order-confirm");
        List<AuditEvent> confirmedEvents = assertEvents(prefix + "-order-confirm", sellerEmail,
                AuditSource.USER, AuditEventType.ORDER_CONFIRMED);
        assertThat(confirmedEvents.getFirst().getSubjectUserId()).isEqualTo(buyerId);

        updateOrderStatus(buyerToken, orderId, "COMPLETED", prefix + "-order-complete");
        assertEvents(prefix + "-order-complete", buyerEmail, AuditSource.USER,
                AuditEventType.ORDER_COMPLETED);

        MvcResult conversation = mvc.perform(post("/api/chats/conversations")
                        .header("Authorization", bearer(sellerToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, prefix + "-conversation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"buyerId\":" + buyerId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        long conversationId = extractLong(ID, conversation.getResponse().getContentAsString());
        assertEvents(prefix + "-conversation", sellerEmail, AuditSource.USER,
                AuditEventType.CONVERSATION_CREATED);

        mvc.perform(post("/api/chats/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(buyerToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, prefix + "-message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Is this item available?\"}"))
                .andExpect(status().isCreated());
        List<AuditEvent> messageEvents = assertEvents(prefix + "-message", buyerEmail, AuditSource.USER,
                AuditEventType.CHAT_MESSAGE_SENT);
        assertThat(messageEvents.getFirst().getMetadata())
                .containsEntry("contentLength", 23)
                .doesNotContainKey("content");

        mvc.perform(patch("/api/chats/conversations/{id}/read", conversationId)
                        .header("Authorization", bearer(sellerToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, prefix + "-read"))
                .andExpect(status().isOk());
        assertEvents(prefix + "-read", sellerEmail, AuditSource.USER,
                AuditEventType.CHAT_MESSAGES_READ);

        mvc.perform(put("/api/locations/me")
                        .header("Authorization", bearer(buyerToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, prefix + "-location-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"District 1\",\"source\":\"MANUAL\","
                                + "\"latitude\":10.77,\"longitude\":106.70}"))
                .andExpect(status().isOk());
        List<AuditEvent> locationEvents = assertEvents(prefix + "-location-update", buyerEmail,
                AuditSource.USER, AuditEventType.USER_LOCATION_UPDATED);
        assertThat(locationEvents.getFirst().getMetadata())
                .doesNotContainKeys("latitude", "longitude", "label");

        mvc.perform(delete("/api/locations/me")
                        .header("Authorization", bearer(buyerToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, prefix + "-location-clear"))
                .andExpect(status().isOk());
        assertEvents(prefix + "-location-clear", buyerEmail, AuditSource.USER,
                AuditEventType.USER_LOCATION_CLEARED);

        long paymentId = createPayment(buyerToken, "PLUS", prefix + "-payment-create");
        assertEvents(prefix + "-payment-create", buyerEmail, AuditSource.USER,
                AuditEventType.MEMBERSHIP_PAYMENT_CREATED);

        mvc.perform(post("/api/membership/payments/{id}/mock-confirm", paymentId)
                        .header("Authorization", bearer(buyerToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, prefix + "-payment-confirm"))
                .andExpect(status().isOk());
        assertEvents(prefix + "-payment-confirm", buyerEmail, AuditSource.USER,
                AuditEventType.MEMBERSHIP_PAYMENT_CONFIRMED,
                AuditEventType.BOOST_CREDITED,
                AuditEventType.MEMBERSHIP_ACTIVATED);

        long disposableProductId = createProduct(sellerToken, prefix + "-product-disposable",
                "Disposable audit jacket");
        mvc.perform(delete("/api/products/{id}", disposableProductId)
                        .header("Authorization", bearer(sellerToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, prefix + "-product-delete"))
                .andExpect(status().isNoContent());
        assertEvents(prefix + "-product-delete", sellerEmail, AuditSource.USER,
                AuditEventType.PRODUCT_DELETED);

        assertThat(sellerId).isPositive();
    }

    @Test
    void adminMutationsAreAttributedToTheAdmin() throws Exception {
        String prefix = "admin-business-" + UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin@pado.test";
        String adminToken = login(adminEmail, "AdminTestPassword123!");
        String planCode = "P4" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase();

        mvc.perform(post("/api/admin/membership/plans")
                        .header("Authorization", bearer(adminToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, prefix + "-plan-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(planCode, "ACTIVE", 10000)))
                .andExpect(status().isCreated());
        assertEvents(prefix + "-plan-create", adminEmail, AuditSource.ADMIN,
                AuditEventType.MEMBERSHIP_PLAN_CREATED);

        mvc.perform(put("/api/admin/membership/plans/{code}", planCode)
                        .header("Authorization", bearer(adminToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, prefix + "-plan-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(planCode, "ACTIVE", 12000)))
                .andExpect(status().isOk());
        assertEvents(prefix + "-plan-update", adminEmail, AuditSource.ADMIN,
                AuditEventType.MEMBERSHIP_PLAN_UPDATED);

        mvc.perform(delete("/api/admin/membership/plans/{code}", planCode)
                        .header("Authorization", bearer(adminToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, prefix + "-plan-hide"))
                .andExpect(status().isOk());
        assertEvents(prefix + "-plan-hide", adminEmail, AuditSource.ADMIN,
                AuditEventType.MEMBERSHIP_PLAN_HIDDEN);

        String customerEmail = prefix + "-customer@pado.test";
        register(customerEmail, "StrongPassword123!");
        String customerToken = login(customerEmail, "StrongPassword123!");
        long paymentId = createPayment(customerToken, "PRO", prefix + "-admin-payment-create");
        mvc.perform(post("/api/admin/membership/payments/{id}/confirm", paymentId)
                        .header("Authorization", bearer(adminToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, prefix + "-admin-payment-confirm"))
                .andExpect(status().isOk());
        assertEvents(prefix + "-admin-payment-confirm", adminEmail, AuditSource.ADMIN,
                AuditEventType.MEMBERSHIP_PAYMENT_CONFIRMED,
                AuditEventType.BOOST_CREDITED,
                AuditEventType.MEMBERSHIP_ACTIVATED);

        String disposableEmail = prefix + "-delete@pado.test";
        long disposableUserId = register(disposableEmail, "StrongPassword123!");
        mvc.perform(delete("/api/admin/users/{id}", disposableUserId)
                        .header("Authorization", bearer(adminToken))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, prefix + "-user-delete"))
                .andExpect(status().isNoContent());
        List<AuditEvent> deletionEvents = assertEvents(prefix + "-user-delete", adminEmail,
                AuditSource.ADMIN, AuditEventType.ADMIN_USER_DELETED);
        assertThat(deletionEvents.getFirst().getTargetId()).isEqualTo(String.valueOf(disposableUserId));
    }

    private List<AuditEvent> assertEvents(String requestId, String actorEmail, AuditSource source,
                                          AuditEventType... expectedTypes) {
        List<AuditEvent> events = auditEvents.findByRequestIdOrderByIdAsc(requestId);
        assertThat(events).extracting(AuditEvent::getEventType).containsExactly(expectedTypes);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getActorEmail()).isEqualToIgnoringCase(actorEmail);
            assertThat(event.getSource()).isEqualTo(source);
            assertThat(event.getMetadata()).containsEntry("requestPath", event.getMetadata().get("requestPath"));
        });
        return events;
    }

    private long register(String email, String password) throws Exception {
        return extractLong(ID, AuthTestHelper.registerAndVerify(mvc, email, password));
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return extract(TOKEN, result.getResponse().getContentAsString());
    }

    private long createProduct(String token, String requestId, String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(token))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractLong(ID, result.getResponse().getContentAsString());
    }

    private long createOrder(String token, long productId, String requestId) throws Exception {
        MvcResult result = mvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"deliveryType":"SHIP","buyerName":"Audit Buyer",
                                 "buyerPhone":"0908123456","deliveryAddress":"12 Le Loi, District 1"}
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractLong(ID, result.getResponse().getContentAsString());
    }

    private void updateOrderStatus(String token, long orderId, String statusName, String requestId)
            throws Exception {
        mvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", bearer(token))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + statusName + "\"}"))
                .andExpect(status().isOk());
    }

    private long createPayment(String token, String planCode, String requestId) throws Exception {
        MvcResult result = mvc.perform(post("/api/membership/payments")
                        .header("Authorization", bearer(token))
                        .header(AuditRequestFilter.REQUEST_ID_HEADER, requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"" + planCode + "\",\"paymentMethod\":\"QR\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractLong(ID, result.getResponse().getContentAsString());
    }

    private String productBody(String title) {
        return """
                {"title":"%s","brand":"Pado","category":"Jacket","size":"M",
                 "color":"Blue","condition":"Like new","price":320000,"originalPrice":900000,
                 "shippingFee":15000,"description":"Business audit product","reason":"Wardrobe cleanup",
                 "sellerLocation":"District 1, HCMC","sold":false,
                 "images":["https://example.test/audit-product.jpg"],"deliveryOptions":["SHIP"]}
                """.formatted(title);
    }

    private String planBody(String code, String statusName, int price) {
        return """
                {"code":"%s","name":"Phase 4 Plan","tagline":"Audit test plan","price":%d,
                 "firstMonthDiscount":"None","benefitsSummary":"Phase 4 audit coverage",
                 "billingCycle":"PER_USE","features":["Audit feature"],"highlight":false,
                 "boostQuota":1,"status":"%s"}
                """.formatted(code, price, statusName);
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
