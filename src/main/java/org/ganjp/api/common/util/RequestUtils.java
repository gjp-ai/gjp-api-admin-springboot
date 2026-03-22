package org.ganjp.api.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.ganjp.api.common.filter.RequestIdFilter;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Utility class for handling request-related operations.
 * Provides methods to retrieve the current request ID.
 */
public class RequestUtils {

    private RequestUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Retrieves the current request ID from the request context.
     * If no request ID is found or there is no active request context,
     * returns a default value of "no-request-id".
     *
     * @return the current request ID or "no-request-id" if unavailable
     */
    public static String getCurrentRequestId() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
                if (requestId != null) {
                    return requestId.toString();
                }
            }
        } catch (Exception e) {
            // Silently handle any exceptions that might occur
        }
        return "no-request-id";
    }
    
    /**
     * Retrieves the current session ID from the request context.
     * If no session ID is found or there is no active request context or session,
     * returns a default value of "no-session".
     *
     * @return the current session ID or "no-session" if unavailable
     */
    public static String getCurrentSessionId() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                Object sessionId = request.getAttribute(RequestIdFilter.SESSION_ID_ATTRIBUTE);
                if (sessionId != null) {
                    return sessionId.toString();
                }
            }
        } catch (Exception e) {
            // Silently handle any exceptions that might occur
        }
        return RequestIdFilter.NO_SESSION;
    }

    /**
     * Gets the current HttpServletRequest from the request context.
     *
     * @return The current HttpServletRequest or null if not available
     */
    public static HttpServletRequest getCurrentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attributes).getRequest();
        }
        return null;
    }
    
    /**
     * Gets the client IP address for the current request
     *
     * @return The client IP address or null if not available
     */
    public static String getCurrentClientIp() {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            return LoggingEnhancer.getClientIp(request);
        }
        return null;
    }
}
