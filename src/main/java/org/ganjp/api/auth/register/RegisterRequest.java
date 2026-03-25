package org.ganjp.api.auth.register;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
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
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^+=])(?=\\S+$).+$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, one special character, and no whitespace"
    )
    private String password;

    @Size(max = 30, message = "Nickname must not exceed 30 characters")
    private String nickname;

    @AssertTrue(message = "At least one of username, email, or mobile (country code and number) must be provided and valid")
    @JsonIgnore
    public boolean isLoginIdentityValid() {
        boolean isUsernameValid = username != null && username.matches("^[A-Za-z0-9._-]{3,30}$");
        boolean isEmailValid = email != null && email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
        boolean isMobileValid = mobileCountryCode != null && mobileNumber != null
                && mobileCountryCode.matches("^[1-9]\\d{0,3}$")
                && mobileNumber.matches("^\\d{4,15}$");

        return isUsernameValid || isEmailValid || isMobileValid;
    }
}
