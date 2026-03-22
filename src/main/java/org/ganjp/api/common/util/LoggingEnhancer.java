package org.ganjp.api.common.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.common.config.LoggingConfig;
import org.ganjp.api.common.filter.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Utility class for enhancing logs with request and user context.
 * Provides methods to enrich logs with request ID, session ID, user information, etc.
 */
@Slf4j
public class LoggingEnhancer {

    // Private constructor to prevent instantiation
    private LoggingEnhancer() {
    }

    /**
     * Constants for header names
     */
    public static final String USER_AGENT_HEADER = "User-Agent";
    
    /**
     * Constants for resource types
     */
    public static final String AUTHENTICATION_RESOURCE = "Authentication";

    /**
     * Enriches the MDC context with request and user information
     * 
     * @param request The HTTP request
     */
    public static void enrichMdc(HttpServletRequest request) {
        if (request == null) {
            return;
        }
        
        // Get request ID and session ID from request attributes
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        String sessionId = (String) request.getAttribute(RequestIdFilter.SESSION_ID_ATTRIBUTE);
        
        // Get authenticated user info if available
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = null;
        String username = null;
        
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            username = auth.getName();
            Object principal = auth.getPrincipal();
            
            if (principal instanceof org.ganjp.api.auth.user.User) {
                userId = ((org.ganjp.api.auth.user.User) principal).getId();
            }
        }
        
        // Get client IP
        String clientIp = getClientIp(request);
        
        // Set MDC context
        LoggingConfig.setMdcContext(requestId, sessionId, userId, username, clientIp);
    }
    
    /**
     * Clears the MDC context
     */
    public static void clearMdc() {
        LoggingConfig.clearMdcContext();
    }
    
    /**
     * Executes a code block with enriched MDC context and cleans up afterward
     * 
     * @param request The HTTP request
     * @param supplier The code block to execute
     * @return The result of the supplier
     */
    public static <T> T withEnrichedContext(HttpServletRequest request, Supplier<T> supplier) {
        // Save original MDC context
        Map<String, String> originalContext = MDC.getCopyOfContextMap();
        
        try {
            // Enrich MDC context
            enrichMdc(request);
            
            // Execute supplier
            return supplier.get();
        } finally {
            // Restore original MDC context
            if (originalContext != null) {
                MDC.setContextMap(originalContext);
            } else {
                MDC.clear();
            }
        }
    }
    
    /**
     * Executes a code block with enriched MDC context and cleans up afterward
     * 
     * @param request The HTTP request
     * @param runnable The code block to execute
     */
    public static void withEnrichedContext(HttpServletRequest request, Runnable runnable) {
        // Save original MDC context
        Map<String, String> originalContext = MDC.getCopyOfContextMap();
        
        try {
            // Enrich MDC context
            enrichMdc(request);
            
            // Execute runnable
            runnable.run();
        } finally {
            // Restore original MDC context
            if (originalContext != null) {
                MDC.setContextMap(originalContext);
            } else {
                MDC.clear();
            }
        }
    }
    
    /**
     * Extract the client IP address from the request
     * 
     * @param request The HTTP request
     * @return The client IP address
     */
    public static String getClientIp(HttpServletRequest request) {
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
    
    /**
     * Logs an exception with contextual information
     * 
     * @param logger The SLF4J logger to use
     * @param method The method where the exception occurred
     * @param e The exception
     * @param customMessage Optional custom message
     */
    public static void logException(org.slf4j.Logger logger, String method, Throwable e, String customMessage) {
        String message = (customMessage != null ? customMessage + ": " : "") + 
                (e.getMessage() != null ? e.getMessage() : "No message");
                
        logger.error("Exception in {}(): {} [type: {}]", method, message, e.getClass().getSimpleName());
    }
}
