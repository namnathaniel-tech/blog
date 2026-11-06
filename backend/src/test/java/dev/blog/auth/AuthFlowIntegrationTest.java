package dev.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.blog.TestcontainersConfiguration;
import dev.blog.common.RateLimiter;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/** End-to-end admin login against real Postgres (Testcontainers) with Spring Session JDBC. */
@SpringBootTest(properties = "blog.admin-email=Owner@Example.com")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

    private static final String ADMIN = "owner@example.com";
    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RateLimiter rateLimiter;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void clean() {
        rateLimiter.reset();
        jdbc.update("delete from admin_login_code");
        jdbc.update("delete from recovery_code");
        jdbc.update("delete from spring_session");
    }

    private void requestCode(String email) throws Exception {
        mvc.perform(post("/api/auth/otp/request").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
    }

    /** Requests a code and returns the one that was emailed. */
    private String requestAndCaptureCode() throws Exception {
        requestCode("OWNER@example.com");
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000)).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("Owner@Example.com");
        Matcher m = SIX_DIGITS.matcher(msg.getText());
        assertThat(m.find()).isTrue();
        org.mockito.Mockito.clearInvocations(mailSender);
        return m.group(1);
    }

    private MvcResult verifyCode(String code) throws Exception {
        return mvc.perform(post("/api/auth/otp/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + ADMIN + "\",\"code\":\"" + code + "\"}")).andReturn();
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void adminRequestStoresOneHashedCodeAndSendsOneMail() throws Exception {
        String code = requestAndCaptureCode();
        List<String> hashes = jdbc.queryForList(
                "select code_hash from admin_login_code where consumed_at is null", String.class);
        assertThat(hashes).hasSize(1);
        assertThat(hashes.getFirst()).doesNotContain(code).startsWith("$2");
    }

    @Test
    void strangerRequestSendsNoMailAndStoresNothing() throws Exception {
        requestCode("stranger@example.com");
        verify(mailSender, after(500).never()).send(any(SimpleMailMessage.class));
        assertThat(jdbc.queryForObject("select count(*) from admin_login_code", Long.class)).isZero();
    }

    @Test
    void correctCodeCreatesPersistentAdminSession() throws Exception {
        String code = requestAndCaptureCode();
        MvcResult result = verifyCode(code);
        assertThat(result.getResponse().getStatus()).isEqualTo(204);

        Cookie session = result.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getMaxAge()).isEqualTo(30 * 24 * 60 * 60);
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("SameSite=Lax");

        mvc.perform(get("/api/public/meta").cookie(session)).andExpect(jsonPath("$.admin").value(true));
        mvc.perform(get("/api/admin/recovery-codes").cookie(session)).andExpect(status().isOk());

        // Stored in Postgres => survives restarts/redeploys.
        assertThat(jdbc.queryForObject(
                "select count(*) from spring_session where principal_name = ?", Long.class, ADMIN)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select max_inactive_interval from spring_session where principal_name = ?", Integer.class, ADMIN))
                .isEqualTo(30 * 24 * 60 * 60);

        // Code is single-use.
        assertThat(verifyCode(code).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void fiveWrongAttemptsBurnTheCode() throws Exception {
        String code = requestAndCaptureCode();
        for (int i = 0; i < 5; i++) {
            assertThat(verifyCode(wrong(code)).getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(verifyCode(code).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void expiredCodeFails() throws Exception {
        String code = requestAndCaptureCode();
        jdbc.update("update admin_login_code set expires_at = now() - interval '1 second'");
        assertThat(verifyCode(code).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void newCodeInvalidatesOldOne() throws Exception {
        String first = requestAndCaptureCode();
        String second = requestAndCaptureCode();
        if (!first.equals(second)) {
            assertThat(verifyCode(first).getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(verifyCode(second).getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void recoveryCodesWorkOnceAndRegenerateInvalidatesOldSet() throws Exception {
        Cookie admin = verifyCode(requestAndCaptureCode()).getResponse().getCookie("SESSION");

        String body = mvc.perform(post("/api/admin/recovery-codes").with(csrf()).cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codes.length()").value(10))
                .andReturn().getResponse().getContentAsString();
        List<String> codes = JsonMapper.builder().build().readTree(body).get("codes")
                .valueStream().map(n -> n.asString()).toList();
        assertThat(codes.getFirst()).matches("[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}");

        MvcResult login = recoveryLogin(codes.get(0).toLowerCase());
        assertThat(login.getResponse().getStatus()).isEqualTo(204);
        Cookie viaRecovery = login.getResponse().getCookie("SESSION");
        mvc.perform(get("/api/admin/recovery-codes").cookie(viaRecovery))
                .andExpect(jsonPath("$.remaining").value(9));

        assertThat(recoveryLogin(codes.get(0)).getResponse().getStatus()).isEqualTo(401);

        mvc.perform(post("/api/admin/recovery-codes").with(csrf()).cookie(viaRecovery)).andExpect(status().isOk());
        assertThat(recoveryLogin(codes.get(1)).getResponse().getStatus()).isEqualTo(401);
    }

    private MvcResult recoveryLogin(String code) throws Exception {
        return mvc.perform(post("/api/auth/recovery").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}")).andReturn();
    }
}
