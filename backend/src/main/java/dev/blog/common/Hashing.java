package dev.blog.common;

import dev.blog.config.BlogProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Salted hashing for values we must never store raw (IP addresses). See D-012. */
@Component
public class Hashing {

    private final byte[] salt;

    public Hashing(BlogProperties props) {
        this.salt = props.ipHashSalt().getBytes(StandardCharsets.UTF_8);
    }

    public String ipHash(String ip) {
        return sha256(ip == null ? "" : ip);
    }

    /** Hash of the client IP. Spoof-resistant behind a proxy because forward-headers-strategy=native. */
    public String clientIpHash(HttpServletRequest request) {
        return ipHash(request.getRemoteAddr());
    }

    String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
