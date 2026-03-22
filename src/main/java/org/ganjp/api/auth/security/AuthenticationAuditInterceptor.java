package org.ganjp.api.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.security.JwtUtils;
import org.ganjp.api.common.audit.AuditService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor for auditing authentication-related requests.
 * Specifically handles login/logout operations that need special audit treatment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationAuditInterceptor implements HandlerInterceptor {

    private final AuditService auditService;
    private final JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Store request start time for duration calculation
        request.setAttribute("auditStartTime", System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String requestUri = request.getRequestURI();
        String method = request.getMethod();

        // Only audit POST requests to authentication endpoints
        if (!"POST".equalsIgnoreCase(method)) {
            return;
        }

        try {
            if (requestUri.contains("/auth/login")) {
                auditLoginAttempt(request, response, ex);
            } else if (requestUri.contains("/auth/logout")) {
                auditLogoutAttempt(request, response, ex);
            } else if (requestUri.contains("/auth/signup")) {
                auditSignupAttempt(request, response, ex);
            } else if (requestUri.contains("/auth/tokens")) {
                auditTokenAttempt(request, response, ex);
            }
        } catch (Exception e) {
            log.error("Error during authentication audit logging", e);
        }
    }

    private void auditLoginAttempt(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        String errorMessage = null;
        String username = extractUsernameFromRequest(request);
        String resultMessage;

        if (ex != null) {
            errorMessage = ex.getMessage();
            resultMessage = "Login failed: " + errorMessage;
        } else if (response.getStatus() == 200) {
            resultMessage = "Login successful";
        } else if (response.getStatus() == 401) {
            resultMessage = "Login failed: Invalid credentials";
        } else {
            resultMessage = "Login failed with status: " + response.getStatus();
        }

        // Extract request data before async call to avoid recycled request object
        Long startTimeMs = (Long) request.getAttribute("auditStartTime");
        AuditService.AuthenticationAuditData auditData = createAuditData(
                request, response, username, resultMessage, startTimeMs);

        auditService.logAuthenticationEvent(auditData);
    }

    private void auditLogoutAttempt(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        String username = extractUsernameFromSecurity();
        String resultMessage;

        if (ex != null) {
            resultMessage = "Logout failed: " + ex.getMessage();
        } else if (response.getStatus() == 200) {
            resultMessage = "Logout successful";
        } else {
            resultMessage = "Logout failed with status: " + response.getStatus();
        }

        // Extract request data before async call to avoid recycled request object
        Long startTimeMs = (Long) request.getAttribute("auditStartTime");
        AuditService.AuthenticationAuditData auditData = createAuditData(
                request, response, username, resultMessage, startTimeMs);

        auditService.logAuthenticationEvent(auditData);
    }

    private void auditSignupAttempt(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        String username = extractUsernameFromRequest(request);
        String resultMessage;

        if (ex != null) {
            resultMessage = "Signup failed: " + ex.getMessage();
        } else if (response.getStatus() == 200 || response.getStatus() == 201) {
            resultMessage = "Signup successful";
        } else if (response.getStatus() == 400 || response.getStatus() == 422) {
            resultMessage = "Signup failed: Validation error";
        } else if (response.getStatus() == 409) {
            resultMessage = "Signup failed: User already exists";
        } else {
            resultMessage = "Signup failed with status: " + response.getStatus();
        }

        // Extract request data before async call to avoid recycled request object
        Long startTimeMs = (Long) request.getAttribute("auditStartTime");
        AuditService.AuthenticationAuditData auditData = createAuditData(
                request, response, username, resultMessage, startTimeMs);

        auditService.logAuthenticationEvent(auditData);
    }

    private void auditTokenAttempt(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        String username = extractUsernameFromRequest(request);
        String resultMessage;

        // Determine result based on response status and exception
        if (ex != null) {
            resultMessage = "Token operation failed: " + ex.getMessage();
        } else if (response.getStatus() == 200) {
            resultMessage = "Token operation successful";
        } else if (response.getStatus() == 401) {
            resultMessage = "Token operation failed: Unauthorized";
        } else if (response.getStatus() == 400) {
            resultMessage = "Token operation failed: Bad request";
        } else {
            resultMessage = "Token operation failed with status: " + response.getStatus();
        }

        // Extract request data before async call to avoid recycled request object
        Long startTimeMs = (Long) request.getAttribute("auditStartTime");
        AuditService.AuthenticationAuditData auditData = createAuditData(
                request, response, username, resultMessage, startTimeMs);

        auditService.logAuthenticationEvent(auditData);
    }

    /**
     * Extract username from request parameters or body (for failed login attempts)
     */
    private String extractUsernameFromRequest(HttpServletRequest request) {
        // Try to get username from request parameters first (for form-based auth)
        String username = request.getParameter("username");
        if (username != null) {
            return username;
        }

        // Try to get username from request attributes (set by controller)
        Object usernameAttr = request.getAttribute("loginUsername");
        if (usernameAttr instanceof String) {
            return (String) usernameAttr;
        }

        // For successful logins, try to get from security context
        return extractUsernameFromSecurity();
    }

    /**
     * Extract username from Spring Security context
     */
    private String extractUsernameFromSecurity() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && 
                !"anonymousUser".equals(authentication.getPrincipal())) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("Could not extract username from security context", e);
        }
        return null;
    }

    /**
     * Extract user ID from JWT token in the Authorization header
     * @param request HttpServletRequest containing the Authorization header
     * @return User ID extracted from token, or null if not available
     */
    private String extractUserIdFromToken(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7); // Remove "Bearer " prefix
                return jwtUtils.extractUserId(token);
            }
        } catch (Exception e) {
            log.debug("Could not extract user ID from JWT token", e);
        }
        return null;
    }

    /**
     * Extract request data and create audit data object
     */
    private AuditService.AuthenticationAuditData createAuditData(
            HttpServletRequest request,
            HttpServletResponse response,
            String username,
            String resultMessage,
            Long startTimeMs) {
        
        Long durationMs = startTimeMs != null ? System.currentTimeMillis() - startTimeMs : null;
        
        // Extract user ID from JWT token (for authenticated requests)
        String userId = extractUserIdFromToken(request);

        // For login requests, the JWT doesn't exist yet in the Authorization header.
        // The TokenController sets loginUserId after successful authentication.
        if (userId == null) {
            Object loginUserId = request.getAttribute("loginUserId");
            if (loginUserId instanceof String) {
                userId = (String) loginUserId;
            }
        }
        
        return AuditService.AuthenticationAuditData.builder()
                .httpMethod(request.getMethod())
                .endpoint(request.getRequestURI())
                .userId(userId)
                .username(username)
                .resultMessage(resultMessage)
                .statusCode(response.getStatus())
                .ipAddress(getClientIpAddress(request))
                .userAgent(getUserAgent(request))
                .sessionId(getSessionId(request))
                .requestId(getRequestId(request))
                .durationMs(durationMs)
                .build();
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                String clientIp = ip.split(",")[0].trim();
                // Normalize IPv6 localhost to IPv4 for better readability
                if ("0:0:0:0:0:0:0:1".equals(clientIp) || "::1".equals(clientIp)) {
                    return "127.0.0.1";
                }
                return clientIp;
            }
        }

        String remoteAddr = request.getRemoteAddr();
        // Normalize IPv6 localhost to IPv4 for better readability
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) {
            return "127.0.0.1";
        }
        return remoteAddr;
    }

    /**
     * Get user agent from request
     */
    private String getUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    /**
     * Get session ID from request
     */
    private String getSessionId(HttpServletRequest request) {
        return request.getSession(false) != null ? request.getSession(false).getId() : "no-session";
    }

    /**
     * Get request ID from request attributes
     */
    private String getRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId != null ? requestId.toString() : "no-req";
    }
}
