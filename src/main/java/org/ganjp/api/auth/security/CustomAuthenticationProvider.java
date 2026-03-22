package org.ganjp.api.auth.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.token.LoginRequest;
import org.ganjp.api.auth.user.User;
import org.ganjp.api.auth.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.ganjp.api.auth.user.AccountStatus;

@Slf4j
@Component
@RequiredArgsConstructor
@Transactional
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String credential = authentication.getName();
        String password = authentication.getCredentials().toString();
        
        // Extract login request from authentication details
        LoginRequest loginRequest = extractLoginRequest(authentication);
        
        // Find the user based on provided credentials
        Optional<User> userOptional = findUser(credential, loginRequest);
        
        // If user not found, just throw exception (no need to update login metrics as user doesn't exist)
        if (userOptional.isEmpty()) {
            throw new BadCredentialsException("Invalid username");
        }
        
        User user = userOptional.get();
        
        // Verify password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            // Record login failure for known user with wrong password
            updateLoginFailureForKnownUser(user);
            throw new BadCredentialsException("Invalid password");
        }
        
        // Check account status
        validateAccountStatus(user);
        
        // Return the authenticated token with authorities
        return new UsernamePasswordAuthenticationToken(
                user, 
                password, 
                user.getAuthorities()
        );
    }

    private LoginRequest extractLoginRequest(Authentication authentication) {
        if (authentication.getDetails() instanceof LoginRequest request) {
            return request;
        }
        return null;
    }
    
    private Optional<User> findUser(String credential, LoginRequest loginRequest) {
        if (loginRequest != null) {
            // First validate that only one login method is provided
            if (!loginRequest.isValidLoginMethod()) {
                throw new BadCredentialsException("Please provide exactly one login method: username, email, or mobile number");
            }
            
            if (loginRequest.getEmail() != null && !loginRequest.getEmail().isEmpty()) {
                // Email login
                return userRepository.findByEmail(loginRequest.getEmail());
            } else if (loginRequest.getMobileNumber() != null && !loginRequest.getMobileNumber().isEmpty()
                    && loginRequest.getMobileCountryCode() != null && !loginRequest.getMobileCountryCode().isEmpty()) {
                // Mobile login
                return userRepository.findByMobileCountryCodeAndMobileNumber(
                        loginRequest.getMobileCountryCode(), loginRequest.getMobileNumber());
            } else {
                // Username login
                return userRepository.findByUsername(loginRequest.getUsername());
            }
        } else {
            // Default to username login if no loginRequest is provided
            Optional<User> userOptional = userRepository.findByUsername(credential);
            
            // If not found, try with email as a fallback (if it looks like an email)
            if (userOptional.isEmpty() && credential != null && credential.contains("@")) {
                userOptional = userRepository.findByEmail(credential);
            }
            return userOptional;
        }
    }

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    /**
     * Update login failure metrics for a known user with an invalid password.
     * Automatically locks the account after MAX_FAILED_ATTEMPTS consecutive failures.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void updateLoginFailureForKnownUser(User user) {
        try {
            LocalDateTime now = LocalDateTime.now();

            log.info("Attempting to update login failures for user: id={}, username={}",
                    user.getId(), user.getUsername());

            if (user.getId() == null || user.getId().isEmpty()) {
                log.error("Cannot update login failures: User ID is null or empty for username: {}", user.getUsername());
                return;
            }

            updateWithJpaQuery(user.getId(), now);

            // Auto-lock account after exceeding max failed attempts
            int newFailedAttempts = user.getFailedLoginAttempts() + 1;
            if (newFailedAttempts >= MAX_FAILED_ATTEMPTS) {
                LocalDateTime lockUntil = now.plusMinutes(LOCK_DURATION_MINUTES);
                userRepository.lockAccount(user.getId(), AccountStatus.locked, lockUntil, now);
                log.warn("Account locked for user {} after {} failed attempts. Locked until {}",
                        user.getUsername(), newFailedAttempts, lockUntil);
            }
        } catch (Exception e) {
            log.error("Failed to update login failure metrics", e);
        }
    }
    
    /**
     * Update using raw JDBC for maximum reliability
     */
    private void updateWithRawJdbc(String userId, LocalDateTime now) {
        Connection conn = null;
        PreparedStatement ps = null;
        
        try {
            // Get direct connection
            conn = jdbcTemplate.getDataSource().getConnection();
            conn.setAutoCommit(false);
            
            // Use prepared statement with timestamp
            String sql = "UPDATE auth_users SET " + 
                       "failed_login_attempts = COALESCE(failed_login_attempts, 0) + 1, " +
                       "last_failed_login_at = ? " + 
                       "WHERE id = ?";
            
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setString(2, userId);
            
            int rows = ps.executeUpdate();
            conn.commit();
            
            log.info("Raw JDBC update result: {} rows affected", rows);
        } catch (Exception e) {
            log.error("Raw JDBC update failed", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception ex) {
                    log.error("Failed to rollback transaction", ex);
                }
            }
        } finally {
            // Clean up resources
            if (ps != null) try { ps.close(); } catch (Exception e) { /* ignore */ }
            if (conn != null) try { conn.close(); } catch (Exception e) { /* ignore */ }
        }
    }
    
    /**
     * Update using the repository's native query
     */
    private void updateWithJpaQuery(String userId, LocalDateTime now) {
        try {
            int rowsUpdated = userRepository.updateLoginFailureByIdNative(userId, now);
            log.info("JPA native query result: {} rows affected", rowsUpdated);
        } catch (Exception e) {
            log.error("JPA native query update failed", e);
        }
    }

    private void validateAccountStatus(User user) {
        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is not active");
        }
        
        if (!user.isAccountNonLocked()) {
            throw new BadCredentialsException("Account is locked");
        }

        if (!user.isAccountNonExpired()) {
            throw new BadCredentialsException("Account has expired");
        }
        
        if (!user.isCredentialsNonExpired()) {
            throw new BadCredentialsException("Credentials have expired");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
