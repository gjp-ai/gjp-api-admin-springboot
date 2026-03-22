package org.ganjp.api.auth.user.profile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.user.profile.ChangePasswordRequest;
import org.ganjp.api.auth.user.profile.UpdateProfileRequest;
import org.ganjp.api.auth.user.profile.UserProfileResponse;
import org.ganjp.api.auth.security.JwtUtils;
import org.ganjp.api.auth.user.profile.UserProfileService;
import org.ganjp.api.common.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for user profile management
 */
@Slf4j
@RestController
@RequestMapping("/v1/profile")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final JwtUtils jwtUtils;

    /**
     * Get current user profile
     */
    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentProfile(
            HttpServletRequest request) {

        String userId = extractUserIdFromRequest(request);
        UserProfileResponse profile = userProfileService.getCurrentUserProfile(userId);

        return ResponseEntity.ok(ApiResponse.success(profile, "Profile retrieved successfully"));
    }

    /**
     * Update current user profile
     */
    @PutMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {

        String userId = extractUserIdFromRequest(httpRequest);
        UserProfileResponse updatedProfile = userProfileService.updateProfile(userId, request);

        return ResponseEntity.ok(ApiResponse.success(updatedProfile, "Profile updated successfully"));
    }

    /**
     * Change password
     */
    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {

        String userId = extractUserIdFromRequest(httpRequest);
        userProfileService.changePassword(userId, request);

        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }

    /**
     * Check if email is available
     */
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Boolean>> checkEmailAvailability(
            @RequestParam String email,
            HttpServletRequest request) {

        String userId = extractUserIdFromRequest(request);
        boolean isAvailable = !userProfileService.isEmailTaken(email, userId);

        return ResponseEntity.ok(ApiResponse.success(isAvailable, "Email availability checked"));
    }

    /**
     * Check if mobile number is available
     */
    @GetMapping("/check-mobile")
    public ResponseEntity<ApiResponse<Boolean>> checkMobileAvailability(
            @RequestParam String countryCode,
            @RequestParam String number,
            HttpServletRequest request) {

        String userId = extractUserIdFromRequest(request);
        boolean isAvailable = !userProfileService.isMobileNumberTaken(countryCode, number, userId);

        return ResponseEntity.ok(ApiResponse.success(isAvailable, "Mobile number availability checked"));
    }

    /**
     * Extract user ID from JWT token in request
     */
    private String extractUserIdFromRequest(HttpServletRequest request) {
        return jwtUtils.extractUserIdFromToken(request);
    }
}