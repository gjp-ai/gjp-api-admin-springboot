package org.ganjp.api.auth.token;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.token.blacklist.TokenBlacklistService;
import org.ganjp.api.auth.token.refresh.RefreshToken;
import org.ganjp.api.auth.token.refresh.RefreshTokenRequest;
import org.ganjp.api.auth.token.refresh.RefreshTokenService;
import org.ganjp.api.auth.token.refresh.TokenRefreshResponse;
import org.ganjp.api.auth.role.UserRole;
import org.ganjp.api.auth.role.UserRoleRepository;
import org.ganjp.api.auth.security.JwtUtils;
import org.ganjp.api.auth.user.AccountStatus;
import org.ganjp.api.auth.user.User;
import org.ganjp.api.auth.user.UserRepository;
import org.ganjp.api.common.util.IpAddressUtils;
import org.ganjp.api.common.exception.ResourceNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Enhanced login method that returns both access and refresh tokens
     * This is the new authentication method that supports token rotation
     */
    @Transactional
    public AuthTokenResponse loginWithDualTokens(LoginRequest loginRequest) {
        // Validate login request has only one authentication method
        if (!loginRequest.isValidLoginMethod()) {
            throw new BadCredentialsException("Please provide exactly one login method: username, email, or mobile number");
        }

        String principal;
        if (loginRequest.getEmail() != null && !loginRequest.getEmail().isEmpty()) {
            principal = loginRequest.getEmail();
        } else if (loginRequest.getUsername() != null && !loginRequest.getUsername().isEmpty()) {
            principal = loginRequest.getUsername();
        } else {
            // For mobile login, combine country code and mobile number as principal
            principal = loginRequest.getMobileCountryCode() + "-" + loginRequest.getMobileNumber();
        }
        
        // Create authentication token
        UsernamePasswordAuthenticationToken authToken = 
            new UsernamePasswordAuthenticationToken(principal, loginRequest.getPassword());
        authToken.setDetails(loginRequest);
        
        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(authToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        // Get the authenticated user
        User user = (User) authentication.getPrincipal();
        
        // Get client IP address for login tracking
        String clientIp = getClientIpFromRequest();
        
        // Update last login timestamp and IP
        LocalDateTime now = LocalDateTime.now();
        user.setLastLoginAt(now);
        user.setLastLoginIp(clientIp);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
        
        // Load user roles
        List<UserRole> activeUserRoles = userRoleRepository.findActiveUserRoles(user, LocalDateTime.now());
        List<String> roleCodes = activeUserRoles.stream()
                .map(userRole -> userRole.getRole().getCode())
                .toList();
        
        // Create authorities
        List<SimpleGrantedAuthority> authorities = activeUserRoles.stream()
                .map(userRole -> new SimpleGrantedAuthority("ROLE_" + userRole.getRole().getCode()))
                .toList();
        
        // Generate access token
        String accessToken = jwtUtils.generateTokenWithAuthorities(user, authorities, user.getId());
        
        // Generate refresh token using RefreshTokenService
        RefreshToken refreshTokenEntity = refreshTokenService.createRefreshToken(user.getId());
        String refreshToken = refreshTokenEntity.getTokenValue();
        
        log.debug("Generated dual tokens for user {}: access token and refresh token", user.getUsername());
        
        // Return enhanced response with both tokens
        return AuthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtils.extractExpirationTimestamp(accessToken) - System.currentTimeMillis())
                .username(user.getUsername())
                .email(user.getEmail())
                .mobileCountryCode(user.getMobileCountryCode())
                .mobileNumber(user.getMobileNumber())
                .nickname(user.getNickname())
                .accountStatus(user.getAccountStatus())
                .lastLoginAt(user.getLastLoginAt())
                .lastLoginIp(user.getLastLoginIp())
                .roleCodes(roleCodes)
                .build();
    }

    /**
     * Refresh access token using a valid refresh token
     * Implements token rotation - old refresh token is revoked and new tokens are issued
     */
    @Transactional
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {
        String refreshTokenValue = request.getRefreshToken();
        
        // Validate refresh token
        RefreshToken refreshToken = refreshTokenService.validateRefreshToken(refreshTokenValue)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));
        
        // Get user details
        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", refreshToken.getUserId()));
        
        // Check if user account is still active and valid
        if (!user.isActive() || user.getAccountStatus() != AccountStatus.active) {
            // Revoke the refresh token for security
            refreshTokenService.revokeRefreshToken(refreshTokenValue);
            throw new BadCredentialsException("User account is not active");
        }
        
        // Load user roles
        List<UserRole> activeUserRoles = userRoleRepository.findActiveUserRoles(user, LocalDateTime.now());
        List<String> roleCodes = activeUserRoles.stream()
                .map(userRole -> userRole.getRole().getCode())
                .toList();
        
        // Create authorities
        List<SimpleGrantedAuthority> authorities = activeUserRoles.stream()
                .map(userRole -> new SimpleGrantedAuthority("ROLE_" + userRole.getRole().getCode()))
                .toList();
        
        // Rotate tokens - revoke old refresh token and create new tokens
        RefreshToken newRefreshTokenEntity = refreshTokenService.rotateRefreshToken(refreshTokenValue, refreshToken.getUserId());
        String newRefreshToken = newRefreshTokenEntity.getTokenValue();
        
        // Generate new access token
        String newAccessToken = jwtUtils.generateAccessTokenForRefresh(user, authorities, user.getId());
        
        log.debug("Token refresh successful for user {}: rotated refresh token and generated new access token", user.getUsername());
        
        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtils.extractExpirationTimestamp(newAccessToken) - System.currentTimeMillis())
                .build();
    }

    /**
     * Enhanced logout method that revokes both access and refresh tokens
     */
    @Transactional
    public void revokeTokens(LogoutRequest logoutRequest, HttpServletRequest request) {
        try {
            // Extract and blacklist access token
            String authHeader = request.getHeader("Authorization");
            String userId = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String accessToken = authHeader.substring(7);

                // Extract token information for blacklisting
                String tokenId = jwtUtils.extractTokenId(accessToken);
                long expirationTime = jwtUtils.extractExpirationTimestamp(accessToken);
                userId = jwtUtils.extractUserId(accessToken);

                if (tokenId != null) {
                    tokenBlacklistService.blacklistToken(tokenId, expirationTime);
                    log.debug("Access token blacklisted: {}", tokenId);
                }
            }

            // Handle logout from all devices
            if (logoutRequest.isLogoutFromAllDevices() && userId != null) {
                int revoked = refreshTokenService.revokeAllUserTokens(userId);
                log.debug("Logout from all devices: revoked {} refresh tokens for user {}", revoked, userId);
            } else if (logoutRequest.getRefreshToken() != null && !logoutRequest.getRefreshToken().trim().isEmpty()) {
                // Revoke only the provided refresh token
                refreshTokenService.revokeRefreshToken(logoutRequest.getRefreshToken());
                log.debug("Refresh token revoked during logout");
            }

        } catch (Exception e) {
            log.warn("Failed to fully process logout tokens: {}", e.getMessage());
            // Continue with logout even if token revocation fails
        }

        // Clear the security context
        SecurityContextHolder.clearContext();
        log.debug("Logout completed successfully");
    }
    
    /**
     * Get the client's IP address from the current request context.
     */
    private String getClientIpFromRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                log.debug("No request attributes available");
                return "unknown";
            }
            return IpAddressUtils.getClientIp(attributes.getRequest());
        } catch (Exception e) {
            log.warn("Failed to determine client IP address", e);
            return "unknown";
        }
    }

}