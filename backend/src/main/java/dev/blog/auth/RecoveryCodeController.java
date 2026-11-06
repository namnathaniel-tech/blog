package dev.blog.auth;

import dev.blog.auth.AuthDtos.RecoveryCodesResponse;
import dev.blog.auth.AuthDtos.RecoveryStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (secured by path in SecurityConfig). */
@RestController
@RequestMapping("/api/admin/recovery-codes")
class RecoveryCodeController {

    private final RecoveryCodeService recovery;

    RecoveryCodeController(RecoveryCodeService recovery) {
        this.recovery = recovery;
    }

    @GetMapping
    RecoveryStatusResponse status() {
        return new RecoveryStatusResponse(recovery.remaining());
    }

    @PostMapping
    RecoveryCodesResponse regenerate() {
        return new RecoveryCodesResponse(recovery.regenerate());
    }
}
