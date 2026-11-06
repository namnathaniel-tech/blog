package dev.blog.common;

import dev.blog.common.RateLimiter.Limit;
import java.time.Duration;

/** All rate limits in one place. Keep in sync with docs/ARCHITECTURE.md §Rate limits. */
public final class Limits {

    public static final Limit OTP_REQUEST_PER_IP = new Limit("otp-request-ip", 3, Duration.ofMinutes(15));
    /** Silent cap on emails sent to the admin address, regardless of requester. */
    public static final Limit OTP_EMAIL_GLOBAL = new Limit("otp-email-global", 10, Duration.ofHours(1));
    public static final Limit AUTH_VERIFY_PER_IP = new Limit("auth-verify-ip", 10, Duration.ofMinutes(15));

    private Limits() {
    }
}
