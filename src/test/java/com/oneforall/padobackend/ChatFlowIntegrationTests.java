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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatFlowIntegrationTests {
    private static final Pattern TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern ID = Pattern.compile("\\\"id\\\":(\\d+)");

    @Autowired
    MockMvc mvc;

    @Test
    void conversationsMessagesAuthorizationAndReadStateWork() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String sellerEmail = "chat-seller-" + suffix + "@pado.test";
        String buyerEmail = "chat-buyer-" + suffix + "@pado.test";
        String otherEmail = "chat-other-" + suffix + "@pado.test";
        String password = "StrongPassword123!";

        register(sellerEmail, password);
        long buyerId = register(buyerEmail, password);
        register(otherEmail, password);
        String sellerToken = login(sellerEmail, password);
        String buyerToken = login(buyerEmail, password);
        String otherToken = login(otherEmail, password);
        long productId = createProduct(sellerToken, "Chat flow jacket");

        mvc.perform(get("/api/chats/conversations"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/chats/conversations")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conversationBody(productId)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/orders")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId)))
                .andExpect(status().isCreated());

        MvcResult created = mvc.perform(post("/api/chats/conversations")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sellerConversationBody(productId, buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.buyerEmail").value(buyerEmail))
                .andExpect(jsonPath("$.sellerEmail").value(sellerEmail))
                .andExpect(jsonPath("$.currentUserRole").value("SELLER"))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andReturn();
        long conversationId = extractLong(ID, created.getResponse().getContentAsString());

        mvc.perform(post("/api/chats/conversations")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conversationBody(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId))
                .andExpect(jsonPath("$.currentUserRole").value("BUYER"));

        mvc.perform(get("/api/chats/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/chats/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  Món này còn không bạn?  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Món này còn không bạn?"))
                .andExpect(jsonPath("$.mine").value(true));

        mvc.perform(post("/api/chats/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/chats/conversations")
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(conversationId))
                .andExpect(jsonPath("$[0].currentUserRole").value("SELLER"))
                .andExpect(jsonPath("$[0].lastMessage").value("Món này còn không bạn?"))
                .andExpect(jsonPath("$[0].unreadCount").value(1));

        mvc.perform(get("/api/chats/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mine").value(false))
                .andExpect(jsonPath("$[0].senderEmail").value(buyerEmail));

        mvc.perform(patch("/api/chats/conversations/{id}/read", conversationId)
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1));

        mvc.perform(post("/api/chats/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Món này vẫn còn nhé.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mine").value(true));

        mvc.perform(get("/api/chats/conversations")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastMessage").value("Món này vẫn còn nhé."))
                .andExpect(jsonPath("$[0].unreadCount").value(1));

        mvc.perform(patch("/api/chats/conversations/{id}/read", conversationId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    private long register(String email, String password) throws Exception {
        return extractLong(ID, AuthTestHelper.registerAndVerify(mvc, email, password));
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return extract(TOKEN, result.getResponse().getContentAsString());
    }

    private long createProduct(String token, String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractLong(ID, result.getResponse().getContentAsString());
    }

    private String authBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String conversationBody(long productId) {
        return "{\"productId\":" + productId + "}";
    }

    private String sellerConversationBody(long productId, long buyerId) {
        return "{\"productId\":" + productId + ",\"buyerId\":" + buyerId + "}";
    }

    private String orderBody(long productId) {
        return """
                {"productId":%d,"deliveryType":"SHIP","buyerName":"Chat Buyer",
                 "buyerPhone":"0908123456","deliveryAddress":"12 Le Loi, District 1"}
                """.formatted(productId);
    }

    private String productBody(String title) {
        return """
                {"title":"%s","brand":"Pado","category":"Jacket","size":"M",
                 "color":"Blue","condition":"Like new","price":320000,"originalPrice":900000,
                 "shippingFee":15000,"description":"Chat flow integration product","reason":"Wardrobe cleanup",
                 "sellerLocation":"District 1, HCMC","latitude":10.77,"longitude":106.70,"sold":false,
                 "images":["https://example.test/chat-product.jpg"],"deliveryOptions":["MEETUP","SHIP"]}
                """.formatted(title);
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
