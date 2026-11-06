package dev.blog.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request/response records for /api/auth and /api/admin/recovery-codes. Mirrors frontend/src/api/auth.ts. */
final class AuthDtos {

    private AuthDtos() {
    }

    record OtpRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    record OtpVerifyRequest(@NotBlank @Email @Size(max = 254) String email,
                            @NotBlank @Size(max = 12) String code) {
    }

    record RecoveryLoginRequest(@NotBlank @Size(max = 32) String code) {
    }

    record MessageResponse(String message) {
    }

    record RecoveryCodesResponse(List<String> codes) {
    }

    record RecoveryStatusResponse(long remaining) {
    }
}
