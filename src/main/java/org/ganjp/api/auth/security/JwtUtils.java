package org.ganjp.api.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for JWT token generation, parsing, and validation.
 * Handles both access and refresh token operations using HMAC-SHA256 signing.
 */
@Slf4j
@Component
public class JwtUtils {

    private final String secretKey;
    private final long jwtExpiration;
    private final String issuer;

    public JwtUtils(SecurityProperties securityProperties) {
        this.secretKey = securityProperties.getJwt().getSecretKey();
        this.jwtExpiration = securityProperties.getJwt().getExpiration();
        this.issuer = securityProperties.getJwt().getIssuer();
    }

    /**
     * Extract the username (subject) from a JWT token.
     *
     * @param token JWT token string
     * @return username stored in the token's subject claim
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract a specific claim from a JWT token using a resolver function.
     *
     * @param token JWT token string
     * @param claimsResolver function to extract the desired claim
     * @param <T> type of the claim value
     * @return extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Generate an access token with default claims for a user.
     *
     * @param userDetails Spring Security user details
     * @return signed JWT access token
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }
    
    /**
     * Generate an access token with authorities and userId claims.
     *
     * @param userDetails Spring Security user details
     * @param authorities granted authorities to include in the token
     * @param userId user UUID to embed in the token
     * @return signed JWT access token with authorities
     */
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
     * Generate an access token with custom extra claims.
     *
     * @param extraClaims additional claims to include
     * @param userDetails Spring Security user details
     * @return signed JWT access token
     */
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

    /**
     * Validate a JWT token against user details (username match and not expired).
     *
     * @param token JWT token string
     * @param userDetails Spring Security user details to validate against
     * @return true if token is valid
     */
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

    /**
     * Extract granted authorities from a JWT token's "authorities" claim.
     *
     * @param token JWT token string
     * @return list of granted authorities, empty if none present
     */
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

    /**
     * Extract the user ID from a JWT token's "userId" claim.
     *
     * @param token JWT token string
     * @return user UUID string
     */
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