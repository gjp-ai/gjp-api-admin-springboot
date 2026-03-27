package org.ganjp.api.auth.verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.user.AccountStatus;
import org.ganjp.api.auth.user.User;
import org.ganjp.api.auth.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for password reset flow (forgot password).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final VerificationTokenService verificationTokenService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Initiate password reset by generating a token for the user with the given email.
     * Returns true if a token was created (user exists), false otherwise.
     * Always returns the same response to the caller to prevent email enumeration.
     */
    @Transactional
    public boolean requestPasswordReset(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.debug("Password reset requested for non-existent email");
            return false;
        }

        User user = userOpt.get();
        if (user.getAccountStatus() == AccountStatus.suspended) {
            log.debug("Password reset requested for suspended user");
            return false;
        }

        VerificationToken token = verificationTokenService.createToken(
                user.getId(), VerificationTokenType.PASSWORD_RESET);

        // In production, send email here. For now, log the token for dev/testing.
        log.info("PASSWORD RESET TOKEN for user '{}' (email: {}): {}",
                user.getUsername(), email, token.getTokenValue());

        return true;
    }

    /**
     * Reset the user's password using a valid token.
     */
    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        VerificationToken token = verificationTokenService
                .validateToken(tokenValue, VerificationTokenType.PASSWORD_RESET)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset token"));

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(user.getId());

        // Unlock account if it was locked (password reset = re-authentication)
        if (user.getAccountStatus() == AccountStatus.locked) {
            user.setAccountStatus(AccountStatus.active);
            user.setFailedLoginAttempts(0);
            user.setAccountLockedUntil(null);
        }

        userRepository.save(user);

        // Mark token as used
        verificationTokenService.markTokenUsed(token);

        log.info("Password reset successfully for user '{}'", user.getUsername());
    }
}
