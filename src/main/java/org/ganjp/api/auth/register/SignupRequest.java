package org.ganjp.api.auth.register;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user registration requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(
        regexp = "^[A-Za-z0-9._-]{3,30}$",
        message = "Username must match the format: 3-30 characters, alphanumeric, dots, underscores, or hyphens"
    )
    private String username;

    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    @Pattern(
        regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
        message = "Email must be a valid email address"
    )
    private String email;

    @Pattern(
            regexp = "^[1-9]\\d{0,3}$",
            message = "Mobile country code must be a valid numeric code"
    )
    private String mobileCountryCode;

    @Pattern(
            regexp = "^\\d{4,15}$",
            message = "Mobile number must be a valid numeric string"
    )
    private String mobileNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 20, message = "Password must be between 6 and 20 characters")
    @Pattern(
        regexp = "^(?=.*\\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{6,}$",
        message = "Password must contain at least one digit, one lowercase letter, one uppercase letter, one special character, and no whitespace"
    )
    private String password;

    @Size(max = 30, message = "Nickname must not exceed 30 characters")
    private String nickname;

    @AssertTrue(message = "At least one of username, email, or mobile (country code and number) must be provided and valid")
    public boolean isLoginIdentityValid() {
        boolean isUsernameValid = username != null && username.matches("^[A-Za-z0-9._-]{3,30}$");
        boolean isEmailValid = email != null && email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
        boolean isMobileValid = mobileCountryCode != null && mobileNumber != null
                && mobileCountryCode.matches("^[1-9]\\d{0,3}$")
                && mobileNumber.matches("^\\d{4,15}$");

        return isUsernameValid || isEmailValid || isMobileValid;
    }
}
