package dev.blog.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One emailed 6-digit code. Only the BCrypt hash is stored. */
@Entity
@Table(name = "admin_login_code")
public class AdminLoginCode {

    static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    private Instant consumedAt;

    private String requesterIpHash;

    protected AdminLoginCode() {
    }

    AdminLoginCode(String codeHash, Instant createdAt, Instant expiresAt, String requesterIpHash) {
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.requesterIpHash = requesterIpHash;
    }

    /** Records a failed attempt; burns the code once MAX_ATTEMPTS is reached. */
    void recordFailedAttempt(Instant now) {
        attempts++;
        if (attempts >= MAX_ATTEMPTS) {
            consumedAt = now;
        }
    }

    void consume(Instant now) {
        consumedAt = now;
    }

    boolean attemptsExhausted() {
        return attempts >= MAX_ATTEMPTS;
    }

    String codeHash() {
        return codeHash;
    }

    Instant expiresAt() {
        return expiresAt;
    }
}
