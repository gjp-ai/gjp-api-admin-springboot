package org.ganjp.api.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.ganjp.api.auth.config.SecurityProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JwtUtils {

    private final String secretKey;
    private final long jwtExpiration;
    private final long refreshExpiration;
    private final String issuer;

    public JwtUtils(SecurityProperties securityProperties) {
        this.secretKey = securityProperties.getJwt().getSecretKey();
        this.jwtExpiration = securityProperties.getJwt().getExpiration();
        this.refreshExpiration = securityProperties.getJwt().getRefreshExpiration();
        this.issuer = securityProperties.getJwt().getIssuer();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }
    
    public String generateTokenWithAuthorities(UserDetails userDetails, Collection<SimpleGrantedAuthority> authorities, String userId) {
        Map<String, Object> extraClaims = new HashMap<>();
        List<String> authoritiesStrings = authorities.stream()
                .map(SimpleGrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        extraClaims.put("authorities", authoritiesStrings);
        extraClaims.put("userId", userId);
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    /**
     * Generate both access and refresh tokens for a user
     * @param userDetails User details
     * @param authorities User authorities
     * @param userId User ID
     * @return Map containing "accessToken" and "refreshToken"
     */
    public Map<String, String> generateDualTokens(UserDetails userDetails, Collection<SimpleGrantedAuthority> authorities, String userId) {
        // Generate access token
        String accessToken = generateTokenWithAuthorities(userDetails, authorities, userId);
        
        // Generate refresh token (minimal claims, longer expiration)
        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put("userId", userId);
        refreshClaims.put("type", "refresh");
        String refreshToken = buildToken(refreshClaims, userDetails, refreshExpiration);
        
        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", accessToken);
        tokens.put("refreshToken", refreshToken);
        return tokens;
    }

    /**
     * Generate a new access token for refresh operation
     * @param userDetails User details
     * @param authorities User authorities  
     * @param userId User ID
     * @return New access token
     */
    public String generateAccessTokenForRefresh(UserDetails userDetails, Collection<SimpleGrantedAuthority> authorities, String userId) {
        return generateTokenWithAuthorities(userDetails, authorities, userId);
    }

    /**
     * Generate a new refresh token
     * @param userDetails User details
     * @param userId User ID
     * @return New refresh token
     */
    public String generateRefreshToken(UserDetails userDetails, String userId) {
        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put("userId", userId);
        refreshClaims.put("type", "refresh");
        return buildToken(refreshClaims, userDetails, refreshExpiration);
    }

    /**
     * Validate if token is a refresh token
     * @param token JWT token
     * @return true if token is a refresh token
     */
    public boolean isRefreshToken(String token) {
        Claims claims = extractAllClaims(token);
        String tokenType = claims.get("type", String.class);
        return "refresh".equals(tokenType);
    }

    /**
     * Get refresh token expiration time
     * @return Refresh token expiration in milliseconds
     */
    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    private String buildToken(
        Map<String, Object> extraClaims,
        UserDetails userDetails,
        long expiration
    ) {
        // Ensure authorities claim is always present
        extraClaims.putIfAbsent("authorities", Collections.emptyList());
        
        // Generate unique token ID for blacklisting support
        String tokenId = UUID.randomUUID().toString();
        
        return Jwts
                .builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuer(issuer)
                .setId(tokenId) // jti claim for token identification
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())
                .requireIssuer(issuer)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @SuppressWarnings("unchecked")
    public List<GrantedAuthority> extractAuthorities(String token) {
        Claims claims = extractAllClaims(token);
        List<String> authorities = claims.get("authorities", List.class);
        
        if (authorities == null) {
            return Collections.emptyList();
        }
        
        return authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    public String extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("userId", String.class);
    }

    /**
     * Extract user ID from JWT token in the Authorization header
     * @param request HttpServletRequest containing the Authorization header
     * @return User ID extracted from token
     */
    public String extractUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // Remove "Bearer " prefix
            return extractUserId(token);
        }
        return null;
    }

    /**
     * Extract token ID (jti claim) for blacklisting support
     */
    public String extractTokenId(String token) {
        return extractClaim(token, Claims::getId);
    }

    /**
     * Extract token expiration time as timestamp
     */
    public long extractExpirationTimestamp(String token) {
        Date expiration = extractExpiration(token);
        return expiration != null ? expiration.getTime() : 0;
    }

    /**
     * Extract token issued time
     */
    public Date extractIssuedAt(String token) {
        return extractClaim(token, Claims::getIssuedAt);
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}