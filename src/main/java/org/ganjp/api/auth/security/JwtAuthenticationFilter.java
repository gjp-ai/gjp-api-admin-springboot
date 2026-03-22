package org.ganjp.api.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.ganjp.api.auth.session.ActiveUserService;
import org.ganjp.api.auth.blacklist.TokenBlacklistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filter that intercepts each request to validate JWT tokens and authenticate users.
 * This filter extracts JWT tokens from Authorization headers, validates them,
 * and sets the authenticated user in the Spring Security context.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ActiveUserService activeUserService;
    
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String UNKNOWN_IP = "unknown";

    // Modified constructor to accept dependencies directly to avoid circular dependency
    public JwtAuthenticationFilter(JwtUtils jwtUtils, 
                                 @Autowired(required = false) UserDetailsService userDetailsService,
                                 @Autowired(required = false) TokenBlacklistService tokenBlacklistService,
                                 @Autowired(required = false) ActiveUserService activeUserService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.activeUserService = activeUserService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        // Skip JWT processing for OPTIONS requests (CORS preflight)
        if (request.getMethod().equals("OPTIONS")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        final String jwt;
        final String username;

        // Check if Authorization header exists and has Bearer token
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract token (remove "Bearer " prefix)
        jwt = authHeader.substring(BEARER_PREFIX.length());
        
        try {
            username = jwtUtils.extractUsername(jwt);
            
            // Check if token is blacklisted (logged out)
            if (tokenBlacklistService != null) {
                String tokenId = jwtUtils.extractTokenId(jwt);
                if (tokenBlacklistService.isTokenBlacklisted(tokenId)) {
                    logger.debug("Token is blacklisted (logged out): " + tokenId);
                    filterChain.doFilter(request, response);
                    return;
                }
            }
            
            // Authenticate user if token has username and no authentication exists yet 
            // and userDetailsService is not null
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null 
                && userDetailsService != null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                
                // Validate token and set authentication if valid
                if (jwtUtils.isTokenValid(jwt, userDetails)) {
                    List<GrantedAuthority> authorities = jwtUtils.extractAuthorities(jwt);

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            authorities
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    
                    // Track active user in memory
                    if (activeUserService != null) {
                        String userId = jwtUtils.extractUserId(jwt);
                        String userAgent = request.getHeader("User-Agent");
                        String ipAddress = getClientIpAddress(request);
                        
                        if (activeUserService.isUserActive(userId)) {
                            // User is already active, just update last activity
                            activeUserService.updateLastActivity(userId);
                        } else {
                            // Register new active user
                            activeUserService.registerActiveUser(userId, username, userAgent, ipAddress);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log error but do not block the request
            logger.error("JWT authentication failed: " + e.getMessage(), e);
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * Helper method to extract client IP address from request
     * Handles various proxy headers to get the real client IP
     * 
     * @param request HTTP servlet request
     * @return Client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        
        if (ipAddress == null || ipAddress.isEmpty() || UNKNOWN_IP.equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("X-Real-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || UNKNOWN_IP.equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || UNKNOWN_IP.equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || UNKNOWN_IP.equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        
        // Handle multiple IPs in X-Forwarded-For (take the first one)
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }
        
        return ipAddress != null ? ipAddress : UNKNOWN_IP;
    }
}