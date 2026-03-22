package org.ganjp.api.auth.token;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.auth.user.AccountStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced login response with dual token support (access + refresh tokens).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenResponse {
    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private long expiresIn; // Access token expiration in seconds
    private String username;
    private String email;
    private String mobileCountryCode;
    private String mobileNumber;
    private String nickname;
    private AccountStatus accountStatus;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime lastFailedLoginAt;
    private int failedLoginAttempts;
    @Builder.Default
    private List<String> roleCodes = new ArrayList<>();
}
