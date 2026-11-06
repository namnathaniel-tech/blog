package dev.blog.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Explicit Spring Session cookie settings. Boot's server.servlet.session.cookie.* properties were
 * NOT applied to the Spring Session cookie (verified by AuthFlowIntegrationTest), so we set them here. Max-Age follows spring.session.timeout.
 * The cookie is persistent (Max-Age = session timeout) so closing the browser doesn't log the admin out.
 */
@Configuration
public class SessionCookieConfig {

    @Bean
    CookieSerializer cookieSerializer(Environment env) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(env.getProperty("server.servlet.session.cookie.secure", Boolean.class, true));
        Duration timeout = env.getProperty("spring.session.timeout", Duration.class, Duration.ofDays(30));
        serializer.setCookieMaxAge((int) timeout.toSeconds());
        return serializer;
    }
}
