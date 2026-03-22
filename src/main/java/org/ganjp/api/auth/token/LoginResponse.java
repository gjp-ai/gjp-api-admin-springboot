package org.ganjp.api.auth.token;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.auth.user.AccountStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
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