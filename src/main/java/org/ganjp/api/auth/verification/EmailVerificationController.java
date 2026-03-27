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
 * Controller for email verification operations.
 *
 * Endpoints:
 * - POST /v1/auth/email/verify              — Verify email with a token
 * - POST /v1/auth/email/resend-verification  — Resend the verification email
 */
@Slf4j
@RestController
@RequestMapping("/v1/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    /**
     * Verify a user's email address using a token.
     */
    @PreAuthorize("permitAll()")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verifyEmail(request.getToken());

        return ResponseEntity.ok(
                ApiResponse.success(null, "Email verified successfully"));
    }

    /**
     * Resend verification email. Always returns 200 to prevent email enumeration.
     */
    @PreAuthorize("permitAll()")
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resendVerification(request.getEmail());

        // Always return success to prevent email enumeration
        return ResponseEntity.ok(
                ApiResponse.success(null, "If an account with that email exists and is pending verification, a verification email has been sent"));
    }
}
