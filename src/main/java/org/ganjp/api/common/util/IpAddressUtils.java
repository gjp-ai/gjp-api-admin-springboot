package org.ganjp.api.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Shared utility for extracting the client IP address from HTTP requests.
 * Handles proxy headers, IPv6 loopback normalization, and comma-separated lists.
 *
 * <p>All auth and audit code should call this single implementation to ensure
 * consistent IP resolution across the application.</p>
 */
public final class IpAddressUtils {

    private static final String IPV6_LOOPBACK_LONG = "0:0:0:0:0:0:0:1";
    private static final String IPV6_LOOPBACK_SHORT = "::1";
    private static final String IPV4_LOOPBACK = "127.0.0.1";

    private static final String[] PROXY_HEADERS = {
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
        "CF-Connecting-IP",
        "True-Client-IP"
    };

    private IpAddressUtils() {}

    /**
     * Extract and normalize the client IP address from the request.
     * Checks standard proxy headers first, then falls back to
     * {@code request.getRemoteAddr()}.
     *
     * @param request the HTTP request
     * @return normalized client IP address (IPv6 loopback → 127.0.0.1)
     */
    public static String getClientIp(HttpServletRequest request) {
        for (String header : PROXY_HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For may contain a comma-separated list; take the first
                String clientIp = ip.contains(",") ? ip.split(",")[0].trim() : ip.trim();
                return normalize(clientIp);
            }
        }
        return normalize(request.getRemoteAddr());
    }

    /**
     * Normalize IPv6 loopback addresses to 127.0.0.1 for readability.
     */
    private static String normalize(String ip) {
        if (ip == null) return IPV4_LOOPBACK;
        if (IPV6_LOOPBACK_LONG.equals(ip) || IPV6_LOOPBACK_SHORT.equals(ip)) {
            return IPV4_LOOPBACK;
        }
        return ip;
    }
}
