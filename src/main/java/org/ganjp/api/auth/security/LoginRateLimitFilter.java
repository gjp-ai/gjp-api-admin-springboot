package org.ganjp.api.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.ganjp.api.common.model.ApiResponse;

import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiting filter for login endpoint to prevent brute-force attacks.
 * Limits each IP to a maximum number of login attempts per time window.
 *
 * <p><b>Limitation:</b> This filter uses an in-memory map, so rate limits are
 * per-JVM instance. In a multi-instance deployment, each instance maintains
 * its own counters, effectively multiplying the allowed attempts by the number
 * of instances. For clustered deployments, consider a shared store (e.g. database
 * counter table) instead.</p>
 */
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final long CLEANUP_INTERVAL_MS = 300_000; // 5 minutes
    private static final String LOGIN_PATH = "/v1/auth/tokens";

    private final SecurityProperties securityProperties;
    private final ConcurrentHashMap<String, RateLimitEntry> attempts = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LoginRateLimitFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

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
        long windowMs = securityProperties.getRateLimit().getWindowMs();
        int maxAttempts = securityProperties.getRateLimit().getMaxAttempts();

        // Periodically remove stale entries to prevent unbounded memory growth
        evictStaleEntries(now);

        RateLimitEntry entry = attempts.compute(clientIp, (key, existing) -> {
            if (existing == null || now - existing.windowStart > windowMs) {
                return new RateLimitEntry(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (entry.count.get() > maxAttempts) {
            log.warn("Rate limit exceeded for IP: {} ({} attempts in window)", clientIp, entry.count.get());
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");

            Map<String, String> errors = Map.of("error", "Rate limit exceeded");
            ApiResponse<?> apiResponse = ApiResponse.error(429,
                    "Too many login attempts. Please try again later.", errors);
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Remove entries whose window has expired. Runs at most once every
     * CLEANUP_INTERVAL_MS to avoid scanning on every request.
     */
    private void evictStaleEntries(long now) {
        long windowMs = securityProperties.getRateLimit().getWindowMs();
        long last = lastCleanup.get();
        if (now - last > CLEANUP_INTERVAL_MS && lastCleanup.compareAndSet(last, now)) {
            int removed = 0;
            Iterator<Map.Entry<String, RateLimitEntry>> it = attempts.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, RateLimitEntry> e = it.next();
                if (now - e.getValue().windowStart > windowMs) {
                    it.remove();
                    removed++;
                }
            }
            if (removed > 0) {
                log.debug("Rate limiter cleanup: removed {} stale entries, {} remaining", removed, attempts.size());
            }
        }
    }

    /**
     * Get client IP for rate limiting.
     * Uses getRemoteAddr() as the primary source to prevent X-Forwarded-For
     * header spoofing. Attackers can trivially rotate X-Forwarded-For values
     * to bypass per-IP rate limits. The remote address is set by the TCP
     * connection and cannot be forged.
     *
     * <p>Note: If deployed behind a trusted reverse proxy (Nginx, ALB, etc.),
     * configure the proxy to set a verified header and update this method
     * to trust only that specific header.</p>
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
