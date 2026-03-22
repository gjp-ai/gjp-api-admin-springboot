package org.ganjp.api.auth.token;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.ganjp.api.auth.token.LoginRequest;
import org.ganjp.api.auth.token.LogoutRequest;
import org.ganjp.api.auth.token.RefreshTokenRequest;
import org.ganjp.api.auth.token.LoginResponse;
import org.ganjp.api.common.model.ApiResponse;
import org.ganjp.api.auth.token.AuthTokenResponse;
import org.ganjp.api.auth.token.TokenRefreshResponse;
import org.ganjp.api.auth.security.JwtUtils;
import org.ganjp.api.auth.session.ActiveUserService;
import org.ganjp.api.auth.token.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for authentication and token management operations
 * 
 * This RESTful controller handles all token-related operations including 
 * authentication (login), token refresh, and token revocation (logout).
 * It follows RESTful API design principles by treating tokens as resources
 * and using appropriate HTTP methods:
 * 
 * Base URI: /v1/auth/tokens
 * 
 * Resources:
 * - POST    / : Create new tokens (login)
 * - PUT     / : Refresh tokens
 * - DELETE  / : Revoke tokens (logout)
 * 
 * For user registration, see {@link RegisterController}
 */
@RestController
@RequestMapping("/v1/auth/tokens")
@RequiredArgsConstructor
public class TokenController {

    private final AuthService authService;
    private final ActiveUserService activeUserService;
    private final JwtUtils jwtUtils;

    /**
     * Create new access and refresh tokens (authentication)
     * This is the recommended authentication method with token rotation support
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AuthTokenResponse>> createTokens(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        try {
            // Store request data for audit logging
            String username = extractUsernameFromLoginRequest(loginRequest);
            request.setAttribute("loginUsername", username);
            request.setAttribute("loginRequestData", sanitizeLoginRequest(loginRequest));

            AuthTokenResponse authResponse = authService.loginWithDualTokens(loginRequest);
            
            // Store response data and resource ID for audit logging
            request.setAttribute("loginResponseData", sanitizeAuthTokenResponse(authResponse));
            request.setAttribute("loginResourceId", authResponse.getUsername());
            
            ApiResponse<AuthTokenResponse> response = ApiResponse.<AuthTokenResponse>success(authResponse, "Authentication successful");
            return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .body(response);
        } catch (BadCredentialsException e) {
            // Store username even for failed login
            String username = extractUsernameFromLoginRequest(loginRequest);
            request.setAttribute("loginUsername", username);
            request.setAttribute("loginRequestData", sanitizeLoginRequest(loginRequest));
            
            Map<String, String> errors = new HashMap<>();
            errors.put("error", e.getMessage());
            ApiResponse<AuthTokenResponse> response = ApiResponse.<AuthTokenResponse>error(401, "Unauthorized", errors);
            return ResponseEntity.status(401).body(response);
        } catch (Exception e) {
            // Store username even for failed login
            String username = extractUsernameFromLoginRequest(loginRequest);
            request.setAttribute("loginUsername", username);
            request.setAttribute("loginRequestData", sanitizeLoginRequest(loginRequest));
            
            Map<String, String> errors = new HashMap<>();
            errors.put("error", e.getMessage());
            ApiResponse<AuthTokenResponse> response = ApiResponse.<AuthTokenResponse>error(500, "Internal Server Error", errors);
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Refresh access token using a valid refresh token
     * Implements token rotation - old refresh token is invalidated and new tokens are issued
     */
    @PutMapping
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshTokens(
            @Valid @RequestBody RefreshTokenRequest refreshRequest,
            HttpServletRequest request) {
        try {
            // Store request data for audit logging (excluding sensitive token data)
            request.setAttribute("refreshTokenRequest", sanitizeRefreshTokenRequest(refreshRequest));

            TokenRefreshResponse refreshResponse = authService.refreshToken(refreshRequest);
            
            // Store response data for audit logging
            request.setAttribute("refreshTokenResponse", sanitizeTokenRefreshResponse(refreshResponse));
            
            ApiResponse<TokenRefreshResponse> response = ApiResponse.<TokenRefreshResponse>success(refreshResponse, "Token refresh successful");
            return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .body(response);
        } catch (BadCredentialsException e) {
            Map<String, String> errors = new HashMap<>();
            errors.put("error", e.getMessage());
            ApiResponse<TokenRefreshResponse> response = ApiResponse.<TokenRefreshResponse>error(401, "Invalid or expired refresh token", errors);
            return ResponseEntity.status(401).body(response);
        } catch (Exception e) {
            Map<String, String> errors = new HashMap<>();
            errors.put("error", e.getMessage());
            ApiResponse<TokenRefreshResponse> response = ApiResponse.<TokenRefreshResponse>error(500, "Internal Server Error", errors);
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Revoke tokens (logout)
     * This is the recommended logout method that revokes both access and refresh tokens
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> revokeTokens(
            @Valid @RequestBody LogoutRequest logoutRequest,
            HttpServletRequest request) {
        try {
            // Store request data for audit logging (excluding sensitive token data)
            request.setAttribute("LogoutRequest", sanitizeLogoutRequest(logoutRequest));

            // Extract user ID from the Authorization header before revoking tokens
            String authHeader = request.getHeader("Authorization");
            String userId = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7);
                    userId = jwtUtils.extractUserId(token);
                } catch (Exception e) {
                    // Log the error but continue with logout
                    // User might be logging out with an expired token
                }
            }

            // Revoke tokens through auth service
            authService.revokeTokens(logoutRequest, request);
            
            // Remove user from active user tracking
            if (userId != null) {
                activeUserService.removeActiveUser(userId);
            }
            
            ApiResponse<Void> response = ApiResponse.<Void>success(null, "Logout successful - all tokens revoked");
            return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .body(response);
        } catch (Exception e) {
            Map<String, String> errors = new HashMap<>();
            errors.put("error", e.getMessage());
            ApiResponse<Void> response = ApiResponse.<Void>error(500, "Internal Server Error", errors);
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Helper method to extract username from login request
     */
    private String extractUsernameFromLoginRequest(LoginRequest loginRequest) {
        if (loginRequest.getUsername() != null && !loginRequest.getUsername().trim().isEmpty()) {
            return loginRequest.getUsername();
        } else if (loginRequest.getEmail() != null && !loginRequest.getEmail().trim().isEmpty()) {
            return loginRequest.getEmail();
        } else if (loginRequest.getMobileCountryCode() != null && loginRequest.getMobileNumber() != null) {
            return loginRequest.getMobileCountryCode() + "-" + loginRequest.getMobileNumber();
        }
        return null;
    }

    /**
     * Helper method to sanitize login request for audit logging (remove password)
     */
    private Object sanitizeLoginRequest(LoginRequest loginRequest) {
        Map<String, Object> sanitized = new HashMap<>();
        sanitized.put("username", loginRequest.getUsername());
        sanitized.put("email", loginRequest.getEmail());
        sanitized.put("mobileCountryCode", loginRequest.getMobileCountryCode());
        sanitized.put("mobileNumber", loginRequest.getMobileNumber());
        // Deliberately exclude password for security
        return sanitized;
    }

    /**
     * Helper method to sanitize login response for audit logging (remove token)
     */
    private Object sanitizeLoginResponse(LoginResponse loginResponse) {
        Map<String, Object> sanitized = new HashMap<>();
        sanitized.put("username", loginResponse.getUsername());
        sanitized.put("email", loginResponse.getEmail());
        sanitized.put("accountStatus", loginResponse.getAccountStatus());
        sanitized.put("lastLoginAt", loginResponse.getLastLoginAt());
        sanitized.put("roleCodes", loginResponse.getRoleCodes());
        // Deliberately exclude token for security
        return sanitized;
    }

    /**
     * Helper method to extract user ID from login response
     */
    private String extractUserIdFromResponse(LoginResponse loginResponse) {
        // Since LoginResponse doesn't contain user ID directly, 
        // we could try to extract it from the JWT token or return null
        // For now, return null as user ID is typically handled by security context
        return null;
    }

    /**
     * Helper method to sanitize auth token response for audit logging (remove sensitive tokens)
     */
    private Object sanitizeAuthTokenResponse(AuthTokenResponse authTokenResponse) {
        Map<String, Object> sanitized = new HashMap<>();
        sanitized.put("username", authTokenResponse.getUsername());
        sanitized.put("email", authTokenResponse.getEmail());
        sanitized.put("accountStatus", authTokenResponse.getAccountStatus());
        sanitized.put("lastLoginAt", authTokenResponse.getLastLoginAt());
        sanitized.put("roleCodes", authTokenResponse.getRoleCodes());
        sanitized.put("tokenType", authTokenResponse.getTokenType());
        sanitized.put("expiresIn", authTokenResponse.getExpiresIn());
        // Deliberately exclude access and refresh tokens for security
        return sanitized;
    }

    /**
     * Helper method to sanitize refresh token request for audit logging (remove sensitive token)
     */
    private Object sanitizeRefreshTokenRequest(RefreshTokenRequest refreshRequest) {
        Map<String, Object> sanitized = new HashMap<>();
        sanitized.put("tokenPresent", refreshRequest.getRefreshToken() != null && !refreshRequest.getRefreshToken().trim().isEmpty());
        // Deliberately exclude actual refresh token for security
        return sanitized;
    }

    /**
     * Helper method to sanitize token refresh response for audit logging (remove sensitive tokens)
     */
    private Object sanitizeTokenRefreshResponse(TokenRefreshResponse refreshResponse) {
        Map<String, Object> sanitized = new HashMap<>();
        sanitized.put("tokenType", refreshResponse.getTokenType());
        sanitized.put("expiresIn", refreshResponse.getExpiresIn());
        sanitized.put("tokensGenerated", true);
        // Deliberately exclude access and refresh tokens for security
        return sanitized;
    }

    /**
     * Helper method to sanitize logout request for audit logging (remove sensitive token)
     */
    private Object sanitizeLogoutRequest(LogoutRequest logoutRequest) {
        Map<String, Object> sanitized = new HashMap<>();
        sanitized.put("refreshTokenPresent", logoutRequest.getRefreshToken() != null && !logoutRequest.getRefreshToken().trim().isEmpty());
        sanitized.put("logoutFromAllDevices", logoutRequest.isLogoutFromAllDevices());
        // Deliberately exclude actual refresh token for security
        return sanitized;
    }
}
