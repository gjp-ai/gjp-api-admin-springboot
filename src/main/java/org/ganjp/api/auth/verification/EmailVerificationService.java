package org.ganjp.api.auth.verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.user.AccountStatus;
import org.ganjp.api.auth.user.User;
import org.ganjp.api.auth.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for email verification flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final VerificationTokenService verificationTokenService;

    /**
     * Create an email verification token for a user (called after registration).
     */
    @Transactional
    public void sendVerificationToken(String userId, String email) {
        VerificationToken token = verificationTokenService.createToken(
                userId, VerificationTokenType.EMAIL_VERIFICATION);

        // In production, send email here. For now, log the token for dev/testing.
        log.info("EMAIL VERIFICATION TOKEN for user (email: {}): {}",
                email, token.getTokenValue());
    }

    /**
     * Verify the user's email using a valid token.
     */
    @Transactional
    public void verifyEmail(String tokenValue) {
        VerificationToken token = verificationTokenService
                .validateToken(tokenValue, VerificationTokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired email verification token"));

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getAccountStatus() != AccountStatus.pending_verification) {
            throw new IllegalArgumentException("Email is already verified");
        }

        // Activate the account
        user.setAccountStatus(AccountStatus.active);
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(user.getId());
        userRepository.save(user);

        // Mark token as used
        verificationTokenService.markTokenUsed(token);

        log.info("Email verified successfully for user '{}'", user.getUsername());
    }

    /**
     * Resend verification email. Returns the same response regardless to prevent email enumeration.
     */
    @Transactional
    public boolean resendVerification(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.debug("Resend verification requested for non-existent email");
            return false;
        }

        User user = userOpt.get();
        if (user.getAccountStatus() != AccountStatus.pending_verification) {
            log.debug("Resend verification requested for already-verified user");
            return false;
        }

        sendVerificationToken(user.getId(), email);
        return true;
    }
}
