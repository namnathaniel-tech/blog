package dev.blog.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed view of the {@code blog.*} block in application.yaml. Add new settings here, not via @Value. */
@Validated
@ConfigurationProperties(prefix = "blog")
public record BlogProperties(
        @NotBlank String siteName,
        String siteDescription,
        @NotBlank String baseUrl,
        @NotBlank String version,
        @NotBlank @Email String adminEmail,
        @NotBlank String mailFrom,
        @NotBlank String authorName,
        @NotBlank @Size(min = 16) String ipHashSalt) {

    /** Admin email normalized the same way user input is (trim + lowercase). */
    public String normalizedAdminEmail() {
        return adminEmail.trim().toLowerCase(Locale.ROOT);
    }
}
