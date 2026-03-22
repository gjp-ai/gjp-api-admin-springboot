package org.ganjp.api.auth.user.profile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.user.profile.ChangePasswordRequest;
import org.ganjp.api.auth.user.profile.UpdateProfileRequest;
import org.ganjp.api.auth.user.profile.UserProfileResponse;
import org.ganjp.api.auth.user.User;
import org.ganjp.api.auth.user.UserRepository;
import org.ganjp.api.common.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for managing user profile operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Get current user profile
     */
    public UserProfileResponse getCurrentUserProfile(String userId) {
        User user = getUserById(userId);
        return buildUserProfileResponse(user);
    }

    /**
     * Update current user profile
     */
    @Transactional
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = getUserById(userId);

        // Update fields if provided
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname().trim().isEmpty() ? null : request.getNickname().trim());
        }

        if (request.getEmail() != null) {
            String email = request.getEmail().trim().toLowerCase();
            if (!email.isEmpty()) {
                // Check if email is already taken by another user
                Optional<User> existingUser = userRepository.findByEmail(email);
                if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
                    throw new BusinessException("Email is already in use by another account");
                }
                user.setEmail(email);
            } else {
                user.setEmail(null);
            }
        }

        // Update mobile number (both country code and number must be provided together)
        if (request.getMobileCountryCode() != null || request.getMobileNumber() != null) {
            if (request.getMobileCountryCode() != null && request.getMobileNumber() != null) {
                // Check if mobile number is already taken by another user
                Optional<User> existingUser = userRepository.findByMobileCountryCodeAndMobileNumber(
                        request.getMobileCountryCode(), request.getMobileNumber());
                if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
                    throw new BusinessException("Mobile number is already in use by another account");
                }
                user.setMobileCountryCode(request.getMobileCountryCode());
                user.setMobileNumber(request.getMobileNumber());
            } else {
                // Clear mobile number if one is null
                user.setMobileCountryCode(null);
                user.setMobileNumber(null);
            }
        }

        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(userId);

        User savedUser = userRepository.save(user);
        log.info("User profile updated successfully for user: {}", userId);

        return buildUserProfileResponse(savedUser);
    }

    /**
     * Change user password
     */
    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = getUserById(userId);

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("Current password is incorrect");
        }

        // Check if new password is different from current
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException("New password must be different from current password");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(userId);

        userRepository.save(user);
        log.info("Password changed successfully for user: {}", userId);
    }

    /**
     * Get user by ID with validation
     */
    private User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found: " + userId));
    }

    /**
     * Build user profile response DTO
     */
    private UserProfileResponse buildUserProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .mobileCountryCode(user.getMobileCountryCode())
                .mobileNumber(user.getMobileNumber())
                .accountStatus(user.getAccountStatus())
                .lastLoginAt(user.getLastLoginAt())
                .passwordChangedAt(user.getPasswordChangedAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Check if email exists (excluding current user)
     */
    public boolean isEmailTaken(String email, String excludeUserId) {
        Optional<User> existingUser = userRepository.findByEmail(email.toLowerCase().trim());
        return existingUser.isPresent() && !existingUser.get().getId().equals(excludeUserId);
    }

    /**
     * Check if mobile number exists (excluding current user)
     */
    public boolean isMobileNumberTaken(String countryCode, String number, String excludeUserId) {
        Optional<User> existingUser = userRepository.findByMobileCountryCodeAndMobileNumber(countryCode, number);
        return existingUser.isPresent() && !existingUser.get().getId().equals(excludeUserId);
    }
}