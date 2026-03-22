package org.ganjp.api.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.common.config.LoggingConfig;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that adds a unique request ID to each incoming HTTP request.
 * The request ID is stored in the request attributes and can be retrieved
 * throughout the request processing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String SESSION_ID_ATTRIBUTE = "sessionId";
    public static final String SESSION_ID_HEADER = "X-Session-ID";
    public static final String NO_SESSION = "no-session";

    @Override
    protected void doFilterInternal(
            @org.springframework.lang.NonNull HttpServletRequest request,
            @org.springframework.lang.NonNull HttpServletResponse response,
            @org.springframework.lang.NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        // Generate a unique request ID (UUID v4)
        String requestId = UUID.randomUUID().toString();
        
        // Store the request ID in the request attributes
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        
        // Get session ID only if already exists (don't create a new session)
        HttpSession session = request.getSession(false);
        String sessionId = session != null ? session.getId() : NO_SESSION;
        request.setAttribute(SESSION_ID_ATTRIBUTE, sessionId);
        
        // Add headers to response
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(SESSION_ID_HEADER, sessionId);
        
        // Extract client IP for MDC context
        String clientIp = extractClientIp(request);
        
        // Add to MDC for logging - using centralized logging config
        LoggingConfig.setMdcContext(requestId, sessionId, null, null, clientIp);
        
        // Log the request with its ID and session ID
        log.debug("Processing request with ID: {}, Session ID: {}, URI: {}, Method: {}, Client IP: {}", 
                requestId, sessionId, request.getRequestURI(), request.getMethod(), clientIp);
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Log completion of request processing
            log.debug("Completed request with ID: {}, Session ID: {}", requestId, sessionId);
            
            // Remove from MDC
            LoggingConfig.clearMdcContext();
        }
    }

    /**
     * Extract the client IP address from the request
     * 
     * @param request The HTTP request
     * @return The client IP address
     */
    private String extractClientIp(HttpServletRequest request) {
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
                // Handle comma-separated IPs (take the first one)
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
}
