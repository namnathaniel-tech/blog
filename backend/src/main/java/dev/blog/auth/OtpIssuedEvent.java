package dev.blog.auth;

import java.time.Instant;

/** Published inside the issuing transaction; the mail is sent only after commit. */
record OtpIssuedEvent(String code, Instant expiresAt) {

    @Override
    public String toString() {
        return "OtpIssuedEvent[code=******, expiresAt=" + expiresAt + "]"; // never log the code
    }
}
