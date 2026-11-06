package dev.blog.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.blog.common.Hashing;
import dev.blog.common.RateLimiter;
import dev.blog.config.BlogProperties;
import dev.blog.config.SecurityConfig;
import dev.blog.meta.MetaController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web + security slice for AuthController. Services mocked; session handling real. */
@WebMvcTest({AuthController.class, RecoveryCodeController.class, MetaController.class})
@Import({SecurityConfig.class, AdminSessionService.class, Hashing.class, RateLimiter.class})
@EnableConfigurationProperties(BlogProperties.class)
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired RateLimiter rateLimiter;
    @MockitoBean OtpService otp;
    @MockitoBean RecoveryCodeService recovery;

    @BeforeEach
    void reset() {
        rateLimiter.reset();
    }

    private static String email(String e) {
        return "{\"email\":\"" + e + "\"}";
    }

    @Test
    void requestResponseIsIdenticalForAdminAndStranger() throws Exception {
        String admin = mvc.perform(post("/api/auth/otp/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(email("admin@example.com")))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/auth/otp/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(email("someone@else.com")))
                .andExpect(status().isAccepted())
                .andExpect(content().string(admin));
    }

    @Test
    void fourthRequestFromSameIpIsRateLimited() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/otp/request").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(email("x@y.com")))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(post("/api/auth/otp/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(email("x@y.com")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void requestWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/api/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON).content(email("admin@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidEmailIsValidationError() throws Exception {
        mvc.perform(post("/api/auth/otp/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(email("not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void wrongCodeIs401ProblemDetail() throws Exception {
        given(otp.verify(any(), any())).willReturn(false);
        mvc.perform(post("/api/auth/otp/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void correctCodeLogsInAndLogoutLogsOut() throws Exception {
        given(otp.verify(eq("admin@example.com"), eq("123456"))).willReturn(true);
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/otp/verify").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession();

        mvc.perform(get("/api/public/meta").session(session)).andExpect(jsonPath("$.admin").value(true));
        mvc.perform(get("/api/admin/recovery-codes").session(session)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").with(csrf()).session(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/recovery-codes").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void recoveryCodeLogsIn() throws Exception {
        given(recovery.redeem("ABCD-EFGH-JKMN")).willReturn(true);
        mvc.perform(post("/api/auth/recovery").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ABCD-EFGH-JKMN\"}"))
                .andExpect(status().isNoContent());
        verify(recovery).redeem("ABCD-EFGH-JKMN");
    }

    @Test
    void recoveryEndpointsRequireAdmin() throws Exception {
        mvc.perform(get("/api/admin/recovery-codes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/recovery-codes").with(csrf())).andExpect(status().isUnauthorized());
    }
}
