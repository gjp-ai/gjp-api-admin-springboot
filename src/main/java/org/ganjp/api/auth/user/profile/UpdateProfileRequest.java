package org.ganjp.api.auth.user.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating user profile information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(max = 30, message = "Nickname must not exceed 30 characters")
    @Pattern(regexp = "^[\\p{L}\\p{N}\\s._-]*$", message = "Nickname contains invalid characters")
    private String nickname;

    @Email(message = "Invalid email format")
    @Size(max = 128, message = "Email must not exceed 128 characters")
    private String email;

    @Pattern(regexp = "^[1-9]\\d{0,3}$", message = "Mobile country code must be 1-4 digits starting with non-zero")
    private String mobileCountryCode;

    @Pattern(regexp = "^\\d{4,15}$", message = "Mobile number must be 4-15 digits")
    private String mobileNumber;

    /**
     * Validates that mobile country code and number are both provided or both null
     */
    @jakarta.validation.constraints.AssertTrue(message = "Mobile country code and number must both be provided or both be empty")
    public boolean isMobileInfoValid() {
        return (mobileCountryCode == null && mobileNumber == null) ||
               (mobileCountryCode != null && mobileNumber != null);
    }
}