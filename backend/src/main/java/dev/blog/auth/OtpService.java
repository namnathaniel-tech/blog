package dev.blog.auth;

import dev.blog.common.Limits;
import dev.blog.common.RateLimiter;
import dev.blog.config.BlogProperties;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues and verifies emailed one-time codes for the single admin (D-004, ARCHITECTURE §OTP login). */
@Service
public class OtpService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final AdminLoginCodeRepository codes;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final RateLimiter rateLimiter;
    private final BlogProperties props;
    private final SecureRandom random = new SecureRandom();
    /** Used so non-admin verify attempts cost the same as real ones. */
    private final String dummyHash;

    OtpService(AdminLoginCodeRepository codes, PasswordEncoder encoder, ApplicationEventPublisher events,
               RateLimiter rateLimiter, BlogProperties props) {
        this.codes = codes;
        this.encoder = encoder;
        this.events = events;
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.dummyHash = encoder.encode("000000");
    }

    public boolean isAdminEmail(String email) {
        return email != null && normalizeEmail(email).equals(props.normalizedAdminEmail());
    }

    /**
     * Issues a code if {@code email} is the admin's. Does nothing otherwise — callers must
     * respond identically either way.
     */
    @Transactional
    public void requestCode(String email, String requesterIpHash) {
        if (!isAdminEmail(email)) {
            encoder.encode("000000"); // match the admin path's BCrypt cost so timing doesn't reveal the admin email
            return;
        }
        if (!rateLimiter.tryConsume(Limits.OTP_EMAIL_GLOBAL, "admin")) {
            log.warn("Admin OTP email cap reached; request dropped silently");
            return;
        }
        Instant now = Instant.now();
        codes.invalidateActive(now);
        String code = "%06d".formatted(random.nextInt(1_000_000));
        Instant expiresAt = now.plus(CODE_TTL);
        codes.save(new AdminLoginCode(encoder.encode(code), now, expiresAt, requesterIpHash));
        events.publishEvent(new OtpIssuedEvent(code, expiresAt));
    }

    /** @return true if the code is valid for the admin; the code is then consumed. */
    @Transactional
    public boolean verify(String email, String rawCode) {
        String code = rawCode == null ? "" : rawCode.replaceAll("\\s", "");
        if (!isAdminEmail(email) || !code.matches("\\d{6}")) {
            encoder.matches(code, dummyHash);
            return false;
        }
        Instant now = Instant.now();
        Optional<AdminLoginCode> active = codes.findFirstByConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(now);
        if (active.isEmpty()) {
            encoder.matches(code, dummyHash);
            return false;
        }
        AdminLoginCode stored = active.get();
        if (stored.attemptsExhausted()) {
            stored.consume(now);
            return false;
        }
        if (!encoder.matches(code, stored.codeHash())) {
            stored.recordFailedAttempt(now);
            return false;
        }
        stored.consume(now);
        return true;
    }

    /** Housekeeping: drop codes older than a day. */
    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purgeOldCodes() {
        codes.deleteCreatedBefore(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
