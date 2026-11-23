package com.oneforall.padobackend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAuditUiIntegrationTests {
    @Autowired
    MockMvc mvc;

    @Test
    void auditConsoleAndAssetsArePublicButContainNoEmbeddedCredentials() throws Exception {
        mvc.perform(get("/admin/audit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/audit/"));

        mvc.perform(get("/admin/audit/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/audit/index.html"));

        mvc.perform(get("/admin/audit/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PADO Admin Audit Console")))
                .andExpect(content().string(containsString("id=\"loginForm\"")))
                .andExpect(content().string(not(containsString("AdminTestPassword123!"))));

        mvc.perform(get("/admin/audit/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/admin/audit-events")))
                .andExpect(content().string(not(containsString("AdminTestPassword123!"))));

        mvc.perform(get("/admin/audit/styles.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".detail-drawer")));
    }
}
