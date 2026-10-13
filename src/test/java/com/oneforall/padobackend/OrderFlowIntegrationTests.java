package com.oneforall.padobackend;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderFlowIntegrationTests {
    private static final Pattern TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern ID = Pattern.compile("\\\"id\\\":(\\d+)");

    @Autowired
    MockMvc mvc;

    @Test
    void purchaseSaleHistoryStatusAndCancellationFlowWork() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String sellerEmail = "order-seller-" + suffix + "@pado.test";
        String buyerEmail = "order-buyer-" + suffix + "@pado.test";
        String otherEmail = "order-other-" + suffix + "@pado.test";
        String password = "StrongPassword123!";

        register(sellerEmail, password);
        register(buyerEmail, password);
        register(otherEmail, password);
        String sellerToken = login(sellerEmail, password);
        String buyerToken = login(buyerEmail, password);
        String otherToken = login(otherEmail, password);

        long productId = createProduct(sellerToken, "Order flow jacket");

        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shipOrderBody(productId)))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/orders")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shipOrderBody(productId)))
                .andExpect(status().isConflict());

        MvcResult created = mvc.perform(post("/api/orders")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shipOrderBody(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.deliveryType").value("SHIP"))
                .andExpect(jsonPath("$.itemPrice").value(320000))
                .andExpect(jsonPath("$.shippingFee").value(15000))
                .andExpect(jsonPath("$.totalAmount").value(335000))
                .andExpect(jsonPath("$.buyerEmail").value(buyerEmail))
                .andExpect(jsonPath("$.sellerEmail").value(sellerEmail))
                .andReturn();
        long orderId = extractLong(ID, created.getResponse().getContentAsString());

        mvc.perform(post("/api/orders")
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shipOrderBody(productId)))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/orders/purchases").header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(orderId))
                .andExpect(jsonPath("$[0].productTitle").value("Order flow jacket"));

        mvc.perform(get("/api/orders/sales").header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(orderId));

        mvc.perform(get("/api/orders/{id}", orderId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("Cannot edit active order")))
                .andExpect(status().isConflict());

        mvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("CONFIRMED")))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("CONFIRMED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("COMPLETED")))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("COMPLETED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").exists());

        mvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("CANCELLED")))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sold").value(true));

        long cancelledProductId = createProduct(sellerToken, "Cancelled meetup item");
        MvcResult meetupOrder = mvc.perform(post("/api/orders")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meetupOrderBody(cancelledProductId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shippingFee").value(0))
                .andExpect(jsonPath("$.totalAmount").value(320000))
                .andReturn();
        long cancelledOrderId = extractLong(ID, meetupOrder.getResponse().getContentAsString());

        mvc.perform(patch("/api/orders/{id}/status", cancelledOrderId)
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("CANCELLED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").exists());

        mvc.perform(get("/api/products/{id}", cancelledProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sold").value(false));
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
        return extract(TOKEN, result.getResponse().getContentAsString());
    }

    private long createProduct(String sellerToken, String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractLong(ID, result.getResponse().getContentAsString());
    }

    private String authBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String productBody(String title) {
        return """
                {"title":"%s","brand":"Pado","category":"Jacket","size":"M",
                 "color":"Blue","condition":"Like new","price":320000,"originalPrice":900000,
                 "shippingFee":15000,"description":"Order flow integration product","reason":"Wardrobe cleanup",
                 "sellerLocation":"District 1, HCMC","latitude":10.77,"longitude":106.70,"sold":false,
                 "images":["https://example.test/order-product.jpg"],"deliveryOptions":["MEETUP","SHIP"]}
                """.formatted(title);
    }

    private String shipOrderBody(long productId) {
        return """
                {"productId":%d,"deliveryType":"SHIP","buyerName":"Test Buyer",
                 "buyerPhone":"0908123456","deliveryAddress":"12 Le Loi, District 1",
                 "note":"Call before delivery"}
                """.formatted(productId);
    }

    private String meetupOrderBody(long productId) {
        return """
                {"productId":%d,"deliveryType":"MEETUP","buyerName":"Test Buyer",
                 "buyerPhone":"0908123456","meetupLocation":"Coffee shop, District 1"}
                """.formatted(productId);
    }

    private String statusBody(String status) {
        return "{\"status\":\"" + status + "\"}";
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
