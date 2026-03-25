package org.ganjp.api.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting filter for login endpoint to prevent brute-force attacks.
 * Limits each IP to a maximum number of login attempts per time window.
 */
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MS = 60_000; // 1 minute
    private static final String LOGIN_PATH = "/v1/auth/tokens";

    private final ConcurrentHashMap<String, RateLimitEntry> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Only rate-limit POST to the login endpoint
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().endsWith(LOGIN_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        long now = System.currentTimeMillis();

        RateLimitEntry entry = attempts.compute(clientIp, (key, existing) -> {
            if (existing == null || now - existing.windowStart > WINDOW_MS) {
                return new RateLimitEntry(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (entry.count.get() > MAX_ATTEMPTS) {
            log.warn("Rate limit exceeded for IP: {} ({} attempts in window)", clientIp, entry.count.get());
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");

            String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String body = String.format(
                    "{\"status\":{\"code\":429,\"message\":\"Too many login attempts. Please try again later.\",\"errors\":{\"error\":\"Rate limit exceeded\"}},\"data\":null,\"meta\":{\"serverDateTime\":\"%s\"}}",
                    dateTime);
            response.getWriter().write(body);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Get client IP for rate limiting.
     * Uses getRemoteAddr() as the primary source to prevent X-Forwarded-For
     * header spoofing. Attackers can trivially rotate X-Forwarded-For values
     * to bypass per-IP rate limits. The remote address is set by the TCP
     * connection and cannot be forged.
     *
     * Note: If deployed behind a trusted reverse proxy (Nginx, ALB, etc.),
     * configure the proxy to set a verified header and update this method
     * to trust only that specific header.
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        // Normalize IPv6 loopback to IPv4
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }

    private static class RateLimitEntry {
        final long windowStart;
        final AtomicInteger count;

        RateLimitEntry(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
