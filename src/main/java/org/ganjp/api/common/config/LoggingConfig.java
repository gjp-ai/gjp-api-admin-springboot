package org.ganjp.api.common.config;

import org.ganjp.api.common.filter.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.HashMap;

/**
 * Centralized logging configuration for the application.
 * Provides constants and utility methods for logging.
 */
@Configuration
public class LoggingConfig {

    // MDC key constants
    public static final String MDC_REQUEST_ID_KEY = RequestIdFilter.REQUEST_ID_ATTRIBUTE;
    public static final String MDC_SESSION_ID_KEY = RequestIdFilter.SESSION_ID_ATTRIBUTE;
    public static final String MDC_USER_ID_KEY = "userId";
    public static final String MDC_USERNAME_KEY = "username";
    public static final String MDC_CLIENT_IP_KEY = "clientIp";
    
    // Default values for MDC keys when not available
    public static final String NO_REQUEST_ID = "no-request-id";
    public static final String NO_SESSION_ID = RequestIdFilter.NO_SESSION;
    public static final String NO_USER = "anonymous";
    
    /**
     * Sets common MDC parameters to be used across the application.
     * This creates a context that can be used consistently across all logs.
     *
     * @param requestId The request ID
     * @param sessionId The session ID
     * @param userId The authenticated user ID or null if no user
     * @param username The authenticated username or null if no user
     * @param clientIp The client IP address
     */
    public static void setMdcContext(
            String requestId, 
            String sessionId, 
            String userId, 
            String username, 
            String clientIp) {
        
        if (requestId != null) {
            MDC.put(MDC_REQUEST_ID_KEY, requestId);
        }
        
        if (sessionId != null) {
            MDC.put(MDC_SESSION_ID_KEY, sessionId);
        }
        
        if (userId != null) {
            MDC.put(MDC_USER_ID_KEY, userId);
        }
        
        if (username != null) {
            MDC.put(MDC_USERNAME_KEY, username);
        }
        
        if (clientIp != null) {
            MDC.put(MDC_CLIENT_IP_KEY, clientIp);
        }
    }
    
    /**
     * Clears all MDC context parameters set by setMdcContext.
     */
    public static void clearMdcContext() {
        MDC.remove(MDC_REQUEST_ID_KEY);
        MDC.remove(MDC_SESSION_ID_KEY);
        MDC.remove(MDC_USER_ID_KEY);
        MDC.remove(MDC_USERNAME_KEY);
        MDC.remove(MDC_CLIENT_IP_KEY);
    }
    
    /**
     * Gets the current MDC context as a Map.
     * Useful for capturing context in async operations.
     *
     * @return Map containing all MDC parameters
     */
    public static Map<String, String> captureCurrentMdcContext() {
        Map<String, String> mdcMap = new HashMap<>();
        Map<String, String> currentContext = MDC.getCopyOfContextMap();
        
        if (currentContext != null) {
            mdcMap.putAll(currentContext);
        }
        
        return mdcMap;
    }
    
    /**
     * Applies a previously captured MDC context map to the current thread.
     *
     * @param contextMap The MDC context map to apply
     */
    public static void applyMdcContext(Map<String, String> contextMap) {
        if (contextMap == null) {
            return;
        }
        
        for (Map.Entry<String, String> entry : contextMap.entrySet()) {
            MDC.put(entry.getKey(), entry.getValue());
        }
    }
}
