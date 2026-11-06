package dev.blog.meta;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.blog.config.BlogProperties;
import dev.blog.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Slice test: web layer + real SecurityConfig, no database (runs without Docker). */
@WebMvcTest(MetaController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(BlogProperties.class)
class MetaControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void anonymousGetsMetaWithAdminFalse() throws Exception {
        mvc.perform(get("/api/public/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteName").isNotEmpty())
                .andExpect(jsonPath("$.admin").value(false));
    }

    @Test
    void getIssuesReadableCsrfCookieForTheSpa() throws Exception {
        mvc.perform(get("/api/public/meta"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminSessionReportsAdminTrue() throws Exception {
        mvc.perform(get("/api/public/meta")).andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    void adminApiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/admin/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void unsafeRequestsWithoutCsrfTokenAreRejected() throws Exception {
        mvc.perform(post("/api/public/meta")).andExpect(status().isForbidden());
    }
}
