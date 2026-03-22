package org.ganjp.api.auth.token;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {
    // User can login with username, email, or mobile number
    private String username;
    private String email;
    private String password;
    private String mobileCountryCode; // Used for mobile login
    private String mobileNumber; // Used for mobile login
    
    /**
     * Validates that only one login method is provided.
     * Either username, email, or (mobileCountryCode + mobileNumber) should be provided.
     * @return true if the request is valid
     */
    @AssertTrue(message = "Please provide exactly one login method: username, email, or mobile number with country code")
    public boolean isValidLoginMethod() {
        int count = 0;
        
        // Count how many login methods are provided
        if (username != null && !username.trim().isEmpty()) {
            count++;
        }
        if (email != null && !email.trim().isEmpty()) {
            count++;
        }
        if (mobileCountryCode != null && !mobileCountryCode.trim().isEmpty() 
                && mobileNumber != null && !mobileNumber.trim().isEmpty()) {
            count++;
        }
        
        // Only one login method should be provided
        return count == 1;
    }
}