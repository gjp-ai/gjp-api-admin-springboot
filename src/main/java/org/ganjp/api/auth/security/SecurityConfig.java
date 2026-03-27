package org.ganjp.api.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.ganjp.api.auth.session.ActiveUserService;
import org.ganjp.api.auth.token.blacklist.TokenBlacklistService;
import org.ganjp.api.auth.user.UserRepository;
import org.ganjp.api.common.model.ApiResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Security configuration for the application.
 * Configures authentication, authorization, CORS, CSRF, and other security settings.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;

    /**
     * Create the login rate limit filter bean.
     *
     * @return LoginRateLimitFilter instance
     */
    @Bean
    public LoginRateLimitFilter loginRateLimitFilter() {
        return new LoginRateLimitFilter(securityProperties);
    }

    /**
     * Create the JWT authentication filter bean with required dependencies.
     *
     * @param jwtUtils JWT utility for token operations
     * @param userDetailsService service for loading user details
     * @param tokenBlacklistService service for checking blacklisted tokens
     * @param activeUserService service for tracking active users
     * @return JwtAuthenticationFilter instance
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtils jwtUtils,
                                                          UserDetailsService userDetailsService,
                                                          TokenBlacklistService tokenBlacklistService,
                                                          ActiveUserService activeUserService) {
        return new JwtAuthenticationFilter(jwtUtils, userDetailsService, tokenBlacklistService, activeUserService);
    }

    /**
     * Create the UserDetailsService bean that loads users by username.
     *
     * @return UserDetailsService instance
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    /**
     * Create the AuthenticationManager bean.
     *
     * @param config authentication configuration
     * @return AuthenticationManager instance
     * @throws Exception if authentication manager cannot be created
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Create the password encoder bean using BCrypt.
     *
     * @return PasswordEncoder instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Create the authentication entry point for handling unauthorized requests.
     * Returns a JSON response matching the ApiResponse envelope format.
     *
     * @return AuthenticationEntryPoint instance
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");

            String errorMsg = authException.getMessage() != null
                    ? authException.getMessage() : "Authentication required";
            Map<String, String> errors = Map.of("error", errorMsg);
            ApiResponse<?> apiResponse = ApiResponse.error(401, "Unauthorized", errors);

            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        };
    }

    /**
     * Configure CORS settings from application properties.
     *
     * @return CorsConfigurationSource instance
     */
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

    /**
     * Configure the main security filter chain with JWT authentication,
     * CORS, CSRF disabled, stateless sessions, and public/protected endpoint rules.
     *
     * @param http HttpSecurity builder
     * @param jwtAuthFilter JWT authentication filter
     * @param loginRateLimitFilter login rate limit filter
     * @return SecurityFilterChain instance
     * @throws Exception if security chain cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthFilter,
                                                     LoginRateLimitFilter loginRateLimitFilter, ObjectMapper objectMapper) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> {
                // Public endpoints driven by YAML config
                auth.requestMatchers(securityProperties.getPublicEndpoints().toArray(new String[0])).permitAll();

                // Token endpoint: POST (login) and PUT (refresh) are public; DELETE (logout) requires authentication
                auth.requestMatchers(HttpMethod.POST, "/v1/auth/tokens").permitAll();
                auth.requestMatchers(HttpMethod.PUT, "/v1/auth/tokens").permitAll();

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
                .authenticationEntryPoint(authenticationEntryPoint(objectMapper))
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
