package org.ganjp.api.auth.config;

import org.ganjp.api.auth.user.UserRepository;
import org.ganjp.api.auth.security.JwtAuthenticationFilter;
import org.ganjp.api.auth.security.JwtUtils;
import org.ganjp.api.auth.security.LoginRateLimitFilter;
import org.ganjp.api.auth.blacklist.TokenBlacklistService;
import org.ganjp.api.auth.session.ActiveUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.AuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the application.
 * Configures authentication, authorization, CORS, CSRF, and other security settings.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;

    public SecurityConfig(UserRepository userRepository, SecurityProperties securityProperties) {
        this.userRepository = userRepository;
        this.securityProperties = securityProperties;
    }

    @Bean
    public LoginRateLimitFilter loginRateLimitFilter() {
        return new LoginRateLimitFilter();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtils jwtUtils,
                                                          UserDetailsService userDetailsService,
                                                          TokenBlacklistService tokenBlacklistService,
                                                          ActiveUserService activeUserService) {
        return new JwtAuthenticationFilter(jwtUtils, userDetailsService, tokenBlacklistService, activeUserService);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");

            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String currentDateTime = java.time.LocalDateTime.now().format(formatter);

            // Escape the error message to prevent JSON injection
            String errorMsg = authException.getMessage() != null ? authException.getMessage() : "Authentication required";
            String safeError = errorMsg
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");

            String jsonResponse = String.format(
                "{\"status\":{\"code\":401,\"message\":\"Unauthorized\",\"errors\":{\"error\":\"%s\"}},\"data\":null,\"meta\":{\"serverDateTime\":\"%s\"}}",
                safeError, currentDateTime
            );

            response.getWriter().write(jsonResponse);
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Auth-Token", "Origin", "Accept"));
        configuration.setExposedHeaders(List.of("X-Auth-Token", "Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthFilter, LoginRateLimitFilter loginRateLimitFilter) throws Exception {
        // Use the injected JwtAuthenticationFilter bean instead of creating a new instance
        
        // Start building the authorization rules
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> {
                // First add all public endpoints that don't require authentication
                auth.requestMatchers(securityProperties.getPublicEndpoints().toArray(new String[0])).permitAll();
                
                // Then add all role-restricted endpoints from configuration
                if (securityProperties.getAuthorizedEndpoints() != null) {
                    securityProperties.getAuthorizedEndpoints().forEach(endpoint ->
                        auth.requestMatchers(endpoint.getPattern()).hasAnyAuthority(
                            endpoint.getRoles().toArray(new String[0])
                        )
                    );
                }
                
                // Finally, require authentication for all other endpoints
                auth.anyRequest().authenticated();
            })
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authenticationEntryPoint())
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
            )
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}