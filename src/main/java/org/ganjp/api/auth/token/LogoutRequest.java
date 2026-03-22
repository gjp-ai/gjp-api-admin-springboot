package org.ganjp.api.auth.token;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for logout requests with optional refresh token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoutRequest {
    
    private String refreshToken; // Optional - if provided, will be revoked along with access token
    @Builder.Default
    private boolean logoutFromAllDevices = false; // If true, revoke all refresh tokens for the user
}
