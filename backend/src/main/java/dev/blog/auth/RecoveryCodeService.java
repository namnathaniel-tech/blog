package dev.blog.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates and redeems the 10 backup codes (D-004). Format: XXXX-XXXX-XXXX, Crockford base32.
 *
 * Codes carry 60 random bits, so a fast SHA-256 is sufficient (brute force is infeasible) and lets
 * us look a code up by hash instead of running 10 BCrypt comparisons per attempt — which would
 * otherwise be a cheap way for anyone to burn our CPU.
 */
@Service
public class RecoveryCodeService {

    static final int CODE_COUNT = 10;
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 12;

    private final RecoveryCodeRepository repo;
    private final SecureRandom random = new SecureRandom();

    RecoveryCodeService(RecoveryCodeRepository repo) {
        this.repo = repo;
    }

    /** Replaces all existing codes. Returns the plaintext codes — the only time they're visible. */
    @Transactional
    public List<String> regenerate() {
        repo.deleteAllInBatch();
        Instant now = Instant.now();
        List<String> plain = new ArrayList<>(CODE_COUNT);
        List<RecoveryCode> entities = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            String raw = randomCode();
            plain.add(format(raw));
            entities.add(new RecoveryCode(sha256(raw), now));
        }
        repo.saveAll(entities);
        return plain;
    }

    @Transactional(readOnly = true)
    public long remaining() {
        return repo.countByUsedAtIsNull();
    }

    /** @return true if the code matched an unused code, which is then burned. */
    @Transactional
    public boolean redeem(String input) {
        String normalized = normalize(input);
        if (normalized.length() != CODE_LENGTH) {
            return false;
        }
        return repo.findFirstByCodeHashAndUsedAtIsNull(sha256(normalized))
                .map(code -> {
                    code.markUsed(Instant.now());
                    return true;
                })
                .orElse(false);
    }

    /** Uppercase, drop separators, map Crockford look-alikes (I/L→1, O→0). */
    static String normalize(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : input.toUpperCase(Locale.ROOT).toCharArray()) {
            switch (c) {
                case 'I', 'L' -> sb.append('1');
                case 'O' -> sb.append('0');
                default -> {
                    if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z')) sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String randomCode() {
        char[] chars = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            chars[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(chars);
    }

    static String format(String raw) {
        return raw.substring(0, 4) + '-' + raw.substring(4, 8) + '-' + raw.substring(8);
    }
}
