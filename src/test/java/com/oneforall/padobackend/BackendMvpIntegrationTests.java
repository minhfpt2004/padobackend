package com.oneforall.padobackend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.oneforall.padobackend.AuthTestHelper.registerAndVerify;

@SpringBootTest
@AutoConfigureMockMvc
class BackendMvpIntegrationTests {
    private static final Pattern TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern ID = Pattern.compile("\\\"id\\\":(\\d+)");

    @Autowired
    MockMvc mvc;

    @Test
    void healthCheckIsPublicAndVerifiesTheDatabase() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));
    }

    @Test
    void authenticationCrudOwnershipAndAdminAuthorizationWork() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-" + suffix + "@pado.test";
        String otherEmail = "other-" + suffix + "@pado.test";
        String password = "StrongPassword123!";

        String ownerRegistration = registerAndVerify(mvc, ownerEmail, password);
        org.hamcrest.MatcherAssert.assertThat(ownerRegistration,
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password")));

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(ownerEmail, password)))
                .andExpect(status().isConflict());

        registerAndVerify(mvc, otherEmail, password);

        String ownerToken = login(ownerEmail, password);
        String otherToken = login(otherEmail, password);

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ownerEmail));

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(productBody("Unauthorized")))
                .andExpect(status().isUnauthorized());

        MvcResult created = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("Vintage Jacket")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seller").value(ownerEmail))
                .andReturn();
        long productId = extractLong(ID, created.getResponse().getContentAsString());

        mvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Vintage Jacket"));

        mvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("Illegal Update")))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("Updated Jacket")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Jacket"));

        mvc.perform(get("/api/admin/users").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isForbidden());

        String adminToken = login("admin@pado.test", "AdminTestPassword123!");
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/products/{id}", productId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/products/{id}", productId)).andExpect(status().isNotFound());

        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        return extract(TOKEN, result.getResponse().getContentAsString());
    }

    private String authBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String productBody(String title) {
        return """
                {"title":"%s","brand":"Levi's","category":"Áo khoác","size":"M",
                 "color":"Xanh","condition":"Như mới","price":320000,"originalPrice":1200000,
                 "shippingFee":15000,"description":"Sản phẩm kiểm thử MVP","reason":"Dọn tủ",
                 "sellerLocation":"Quận 1, TP.HCM","latitude":10.77,"longitude":106.70,"sold":false,
                 "images":["https://example.test/product.jpg"],"deliveryOptions":["MEETUP","SHIP"]}
                """.formatted(title);
    }

    private String bearer(String token) { return "Bearer " + token; }

    private String extract(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) throw new AssertionError("Expected value not found in response: " + body);
        return matcher.group(1);
    }

    private long extractLong(Pattern pattern, String body) {
        return Long.parseLong(extract(pattern, body));
    }
}
