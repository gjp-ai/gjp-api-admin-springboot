package org.ganjp.api.auth.register;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.auth.user.AccountStatus;

/**
 * DTO for user registration responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupResponse {
    private String id;
    private String username;
    private String email;
    private String mobileCountryCode;
    private String mobileNumber;
    private String nickname;
    private AccountStatus accountStatus;
    private Boolean active;
}
