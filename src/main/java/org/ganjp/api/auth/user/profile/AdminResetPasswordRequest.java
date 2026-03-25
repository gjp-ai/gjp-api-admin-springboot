package org.ganjp.api.auth.user.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for admin-initiated password reset (no current password required)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminResetPasswordRequest {

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^+=])(?=\\S+$).+$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, one special character, and no whitespace"
    )
    private String password;
}
