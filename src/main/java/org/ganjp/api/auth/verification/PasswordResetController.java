package org.ganjp.api.auth.verification;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.common.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for password reset operations.
 *
 * Endpoints:
 * - POST /v1/auth/password/forgot  — Request a password reset email
 * - POST /v1/auth/password/reset   — Reset password with a valid token
 */
@Slf4j
@RestController
@RequestMapping("/v1/auth/password")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * Request a password reset. Always returns 200 to prevent email enumeration.
     */
    @PreAuthorize("permitAll()")
    @PostMapping("/forgot")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request.getEmail());

        // Always return success to prevent email enumeration
        return ResponseEntity.ok(
                ApiResponse.success(null, "If an account with that email exists, a password reset link has been sent"));
    }

    /**
     * Reset the password using a valid token.
     */
    @PreAuthorize("permitAll()")
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());

        return ResponseEntity.ok(
                ApiResponse.success(null, "Password has been reset successfully"));
    }
}
