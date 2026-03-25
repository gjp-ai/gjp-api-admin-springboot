package org.ganjp.api.auth.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.user.AccountStatus;
import org.ganjp.api.auth.user.User;
import org.ganjp.api.auth.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for recording login failures in a separate transaction.
 * Extracted into its own bean so that @Transactional(REQUIRES_NEW) is
 * properly proxied by Spring AOP (private methods in the caller are not proxied).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginFailureService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    private final UserRepository userRepository;

    /**
     * Record a failed login attempt and auto-lock after threshold.
     * Runs in a NEW transaction so the counter persists even if the
     * caller's transaction rolls back due to BadCredentialsException.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(User user) {
        try {
            LocalDateTime now = LocalDateTime.now();

            if (user.getId() == null || user.getId().isEmpty()) {
                log.error("Cannot update login failures: User ID is null or empty for username: {}", user.getUsername());
                return;
            }

            int rowsUpdated = userRepository.updateLoginFailureByIdNative(user.getId(), now);
            log.debug("Login failure recorded for user {}: {} rows affected", user.getUsername(), rowsUpdated);

            // Auto-lock account after exceeding max failed attempts
            int newFailedAttempts = user.getFailedLoginAttempts() + 1;
            if (newFailedAttempts >= MAX_FAILED_ATTEMPTS) {
                LocalDateTime lockUntil = now.plusMinutes(LOCK_DURATION_MINUTES);
                userRepository.lockAccount(user.getId(), AccountStatus.locked, lockUntil, now);
                log.warn("Account locked for user {} after {} failed attempts. Locked until {}",
                        user.getUsername(), newFailedAttempts, lockUntil);
            }
        } catch (Exception e) {
            log.error("Failed to update login failure metrics for user {}", user.getUsername(), e);
        }
    }
}
