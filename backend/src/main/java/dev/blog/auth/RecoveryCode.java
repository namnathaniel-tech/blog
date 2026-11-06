package dev.blog.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Single-use backup login code. Only the SHA-256 of the normalized code is stored (see RecoveryCodeService). */
@Entity
@Table(name = "recovery_code")
public class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant usedAt;

    protected RecoveryCode() {
    }

    RecoveryCode(String codeHash, Instant createdAt) {
        this.codeHash = codeHash;
        this.createdAt = createdAt;
    }

    void markUsed(Instant now) {
        usedAt = now;
    }
}
