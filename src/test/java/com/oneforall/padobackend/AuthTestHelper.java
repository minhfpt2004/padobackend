package com.oneforall.padobackend;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class AuthTestHelper {
    private static final Pattern DEVELOPMENT_OTP = Pattern.compile("\\\"developmentOtp\\\":\\\"(\\d{6})\\\"");

    private AuthTestHelper() {}

    static String registerAndVerify(MockMvc mvc, String email, String password) throws Exception {
        MvcResult started = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(email, password)))
                .andExpect(status().isAccepted())
                .andReturn();
        Matcher matcher = DEVELOPMENT_OTP.matcher(started.getResponse().getContentAsString());
        if (!matcher.find()) throw new AssertionError("Development OTP not found in test response");

        return mvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"otp\":\"" + matcher.group(1) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static String authBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
