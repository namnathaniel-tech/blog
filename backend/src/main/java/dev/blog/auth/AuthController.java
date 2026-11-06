package dev.blog.auth;

import dev.blog.auth.AuthDtos.MessageResponse;
import dev.blog.auth.AuthDtos.OtpRequest;
import dev.blog.auth.AuthDtos.OtpVerifyRequest;
import dev.blog.auth.AuthDtos.RecoveryLoginRequest;
import dev.blog.common.Hashing;
import dev.blog.common.Limits;
import dev.blog.common.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Admin login endpoints. See ARCHITECTURE §OTP login. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    static final String REQUEST_MESSAGE = "If that address is allowed to log in, a code has been sent.";
    private static final String INVALID = "That code is invalid or has expired.";

    private final OtpService otp;
    private final RecoveryCodeService recovery;
    private final AdminSessionService sessions;
    private final RateLimiter rateLimiter;
    private final Hashing hashing;

    AuthController(OtpService otp, RecoveryCodeService recovery, AdminSessionService sessions,
                   RateLimiter rateLimiter, Hashing hashing) {
        this.otp = otp;
        this.recovery = recovery;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.hashing = hashing;
    }

    @PostMapping("/otp/request")
    ResponseEntity<MessageResponse> requestCode(@Valid @RequestBody OtpRequest body, HttpServletRequest request) {
        String ipHash = hashing.clientIpHash(request);
        rateLimiter.consumeOrThrow(Limits.OTP_REQUEST_PER_IP, ipHash);
        otp.requestCode(body.email(), ipHash);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MessageResponse(REQUEST_MESSAGE));
    }

    @PostMapping("/otp/verify")
    ResponseEntity<Void> verify(@Valid @RequestBody OtpVerifyRequest body,
                                HttpServletRequest request, HttpServletResponse response) {
        rateLimiter.consumeOrThrow(Limits.AUTH_VERIFY_PER_IP, hashing.clientIpHash(request));
        if (!otp.verify(body.email(), body.code())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID);
        }
        sessions.logIn(request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recovery")
    ResponseEntity<Void> recoveryLogin(@Valid @RequestBody RecoveryLoginRequest body,
                                       HttpServletRequest request, HttpServletResponse response) {
        rateLimiter.consumeOrThrow(Limits.AUTH_VERIFY_PER_IP, hashing.clientIpHash(request));
        if (!recovery.redeem(body.code())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID);
        }
        sessions.logIn(request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        sessions.logOut(request);
        return ResponseEntity.noContent().build();
    }
}
