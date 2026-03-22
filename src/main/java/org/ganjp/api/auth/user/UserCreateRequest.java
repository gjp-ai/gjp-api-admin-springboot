package org.ganjp.api.auth.user;

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
import org.ganjp.api.auth.user.AccountStatus;

import java.util.Set;

/**
 * DTO for creating a new user or fully updating an existing user.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    @Pattern(
        regexp = "^[A-Za-z0-9._-]{3,30}$",
        message = "Username must match the format: 3-30 characters, alphanumeric, dots, underscores, or hyphens"
    )
    private String username;

    @Size(max = 30, message = "Nickname must be at most 30 characters")
    private String nickname;

    @Email(message = "Email must be a valid email address")
    @Size(max = 128, message = "Email must be at most 128 characters")
    private String email;

    @Pattern(
        regexp = "^[1-9]\\d{0,3}$",
        message = "Mobile country code must be a valid number between 1-9999"
    )
    private String mobileCountryCode;

    @Pattern(
        regexp = "^\\d{4,15}$",
        message = "Mobile number must be between 4-15 digits"
    )
    private String mobileNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 20, message = "Password must be between 6 and 20 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    private String password;

    private AccountStatus accountStatus;

    private Boolean active;

    private Set<String> roleCodes;

    // Ensure either both mobile fields are provided or neither
    @AssertTrue(message = "Both mobile country code and mobile number must be provided or neither")
    @JsonIgnore
    public boolean isMobileInfoValid() {
        return (mobileCountryCode == null && mobileNumber == null) ||
               (mobileCountryCode != null && mobileNumber != null);
    }

    // Ensure at least one contact method is provided
    @AssertTrue(message = "At least one contact method (email or mobile) is required")
    @JsonIgnore
    public boolean isContactMethodProvided() {
        return email != null || (mobileCountryCode != null && mobileNumber != null);
    }
}
