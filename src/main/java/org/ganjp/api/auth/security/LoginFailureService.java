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

    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;

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
            // Query actual DB value to avoid stale in-memory counter
            int maxAttempts = securityProperties.getAccountLock().getMaxFailedAttempts();
            int lockMinutes = securityProperties.getAccountLock().getLockDurationMinutes();
            int failedAttempts = userRepository.findFailedLoginAttemptsById(user.getId());
            if (failedAttempts >= maxAttempts) {
                LocalDateTime lockUntil = now.plusMinutes(lockMinutes);
                userRepository.lockAccount(user.getId(), AccountStatus.locked, lockUntil, now);
                log.warn("Account locked for user {} after {} failed attempts. Locked until {}",
                        user.getUsername(), failedAttempts, lockUntil);
            }
        } catch (Exception e) {
            log.error("Failed to update login failure metrics for user {}", user.getUsername(), e);
        }
    }
}
