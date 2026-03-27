package org.ganjp.api.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.ganjp.api.auth.session.ActiveUserService;
import org.ganjp.api.auth.token.blacklist.TokenBlacklistService;
import org.ganjp.api.common.util.IpAddressUtils;
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
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ActiveUserService activeUserService;
    
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

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
                    log.debug("Token is blacklisted (logged out): {}", tokenId);
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
                        String ipAddress = IpAddressUtils.getClientIp(request);
                        
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
            // Log at debug level — expired/invalid tokens are normal operational events
            log.debug("JWT authentication failed: {}", e.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }
    
}