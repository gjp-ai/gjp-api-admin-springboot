package org.ganjp.api.auth.token;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.token.LoginRequest;
import org.ganjp.api.auth.token.LogoutRequest;
import org.ganjp.api.auth.token.RefreshTokenRequest;
import org.ganjp.api.auth.register.RegisterRequest;
import org.ganjp.api.auth.token.AuthTokenResponse;
import org.ganjp.api.auth.register.RegisterResponse;
import org.ganjp.api.auth.token.TokenRefreshResponse;
import org.ganjp.api.auth.token.RefreshToken;
import org.ganjp.api.auth.role.Role;
import org.ganjp.api.auth.user.User;
import org.ganjp.api.auth.role.UserRole;
import org.ganjp.api.auth.user.AccountStatus;
import org.ganjp.api.auth.role.RoleRepository;
import org.ganjp.api.auth.user.UserRepository;
import org.ganjp.api.auth.role.UserRoleRepository;
import org.ganjp.api.auth.security.JwtUtils;
import org.ganjp.api.auth.token.TokenBlacklistService;
import org.ganjp.api.common.exception.ResourceNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
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
        String clientIp = getClientIp();
        
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
                .collect(Collectors.toList());
        
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
                .collect(Collectors.toList());
        
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
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String accessToken = authHeader.substring(7);
                
                // Extract token information for blacklisting
                String tokenId = jwtUtils.extractTokenId(accessToken);
                long expirationTime = jwtUtils.extractExpirationTimestamp(accessToken);
                
                if (tokenId != null) {
                    tokenBlacklistService.blacklistToken(tokenId, expirationTime);
                    log.debug("Access token blacklisted: {}", tokenId);
                }
            }
            
            // Revoke refresh token if provided
            if (logoutRequest.getRefreshToken() != null && !logoutRequest.getRefreshToken().trim().isEmpty()) {
                refreshTokenService.revokeRefreshToken(logoutRequest.getRefreshToken());
                log.debug("Refresh token revoked during logout");
            }
            
        } catch (Exception e) {
            log.warn("Failed to fully process logout tokens: {}", e.getMessage());
            // Continue with logout even if token revocation fails
        }
        
        // Clear the security context
        SecurityContextHolder.clearContext();
        log.debug("Enhanced logout completed successfully");
    }
    
    /**
     * Get the client's IP address from the current request
     * This method handles various proxy headers and converts local addresses to a readable format
     */
    // Constants for IP handling
    private static final String UNKNOWN_IP = "unknown";
    private static final String IPV4_LOCALHOST = "127.0.0.1";
    private static final String IPV6_LOCALHOST_LONG = "0:0:0:0:0:0:0:1";
    private static final String IPV6_LOCALHOST_SHORT = "::1";
    
    /**
     * Gets the client's IP address from the request using various header strategies.
     * This enhanced version:
     * 1. Properly handles IPv6 addresses including loopback conversion
     * 2. Checks multiple proxy headers in order of reliability
     * 3. Performs proper validation of IP addresses
     * 4. Has improved error handling and logging
     *
     * @return The client's IP address or "unknown" if it cannot be determined
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                log.debug("No request attributes available");
                return UNKNOWN_IP;
            }
            
            HttpServletRequest request = attributes.getRequest();
            
            // Define headers to check in order of preference
            final String[] PROXY_HEADERS = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "X-Real-IP",
                "CF-Connecting-IP", // Cloudflare
                "True-Client-IP"    // Akamai and Cloudflare
            };
            
            // Check all proxy headers
            for (String header : PROXY_HEADERS) {
                String ip = request.getHeader(header);
                if (isValidIp(ip)) {
                    // For X-Forwarded-For, get first IP which is the client IP
                    if (header.equals("X-Forwarded-For") && ip.contains(",")) {
                        ip = ip.split(",")[0].trim();
                    }
                    log.debug("Client IP found using header {}: {}", header, ip);
                    return normalizeIp(ip);
                }
            }
            
            // Use the remote address as a last resort
            String ip = request.getRemoteAddr();
            log.debug("Using remote address as client IP: {}", ip);
            return normalizeIp(ip);
            
        } catch (Exception e) {
            log.warn("Failed to determine client IP address", e);
            return UNKNOWN_IP;
        }
    }
    
    /**
     * Normalize IP address format (handle IPv6 loopback addresses)
     */
    private String normalizeIp(String ip) {
        if (ip == null) {
            return UNKNOWN_IP;
        }
        
        // Handle IPv6 loopback addresses
        if (IPV6_LOCALHOST_LONG.equals(ip) || IPV6_LOCALHOST_SHORT.equals(ip)) {
            return IPV4_LOCALHOST;
        }
        
        return ip;
    }
    
    /**
     * Check if an IP address string is valid and not empty or "unknown"
     */
    private boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty() || ip.length() > 45) {
            return false;
        }
        
        String[] excludedValues = {UNKNOWN_IP, "undefined", "null", "localhost", IPV4_LOCALHOST, IPV6_LOCALHOST_LONG, IPV6_LOCALHOST_SHORT};
        for (String excluded : excludedValues) {
            if (excluded.equalsIgnoreCase(ip)) {
                // We'll still return local addresses from normalizeIp, but we don't want them from headers
                if (excluded.equals(IPV4_LOCALHOST) || excluded.equals(IPV6_LOCALHOST_LONG) || excluded.equals(IPV6_LOCALHOST_SHORT)) {
                    log.debug("Found local address in header: {}", ip);
                }
                return false;
            }
        }
        
        return true;
    }

    /**
     * Register a new user with ROLE_USER role
     *
     * @param registerRequest The registration request data
     * @return The created user data
     */
    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        // Check if username already exists
        if (registerRequest.getUsername() != null && userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }

        // Check if email already exists
        if (registerRequest.getEmail() != null && userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        // Check if mobile number already exists
        if (registerRequest.getMobileCountryCode() != null && registerRequest.getMobileNumber() != null &&
            userRepository.existsByMobileCountryCodeAndMobileNumber(
                registerRequest.getMobileCountryCode(), registerRequest.getMobileNumber())) {
            throw new IllegalArgumentException("Mobile number is already registered");
        }

        // Get the USER role
        Role userRole = roleRepository.findByCode("USER")
                .orElseThrow(() -> new ResourceNotFoundException("Role", "code", "USER"));

        // Validate that username is provided (now required field)
        if (registerRequest.getUsername() == null || registerRequest.getUsername().isEmpty()) {
            if (registerRequest.getEmail() != null && !registerRequest.getEmail().isEmpty()) {
                registerRequest.setUsername(registerRequest.getEmail().split("@")[0]); // Use email prefix as username if not provided
            } else if (registerRequest.getMobileCountryCode() != null && registerRequest.getMobileNumber() != null) {
                registerRequest.setUsername(registerRequest.getMobileCountryCode() + registerRequest.getMobileNumber()); // Use mobile as username
            } else {
                throw new IllegalArgumentException("Username is required and must be provided");
            }
        }

        // Create new user account with all fields explicitly set to avoid missing defaults
        String uuid = UUID.randomUUID().toString();
        User user = User.builder()
                .id(uuid)
                .username(registerRequest.getUsername())
                .email(registerRequest.getEmail())
                .mobileCountryCode(registerRequest.getMobileCountryCode())
                .mobileNumber(registerRequest.getMobileNumber())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .nickname(registerRequest.getNickname())
                .accountStatus(AccountStatus.pending_verification)
                .passwordChangedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(uuid)
                .updatedBy(uuid)
                .build();

        User savedUser = userRepository.save(user);

        // Create user role relationship
        UserRole userRoleEntity = UserRole.builder()
                .user(savedUser)
                .role(userRole)
                .grantedAt(LocalDateTime.now())
                .grantedBy(uuid)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(uuid)
                .updatedBy(uuid)
                .active(true)
                .build();

        userRoleRepository.save(userRoleEntity);

        return RegisterResponse.builder()
                .id(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .mobileCountryCode(savedUser.getMobileCountryCode())
                .mobileNumber(savedUser.getMobileNumber())
                .nickname(savedUser.getNickname())
                .accountStatus(savedUser.getAccountStatus())
                .active(savedUser.isActive())
                .build();
    }
    
    // Legacy logout method has been removed in favor of enhancedLogout
}