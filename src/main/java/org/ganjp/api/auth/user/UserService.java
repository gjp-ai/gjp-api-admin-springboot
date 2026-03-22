package org.ganjp.api.auth.user;

import lombok.RequiredArgsConstructor;
import org.ganjp.api.auth.user.UserUpsertRequest;
import org.ganjp.api.auth.user.UserPatchRequest;
import org.ganjp.api.auth.role.RoleResponse;
import org.ganjp.api.auth.user.UserResponse;
import org.ganjp.api.auth.role.Role;
import org.ganjp.api.auth.user.User;
import org.ganjp.api.auth.role.UserRole;
import org.ganjp.api.auth.user.AccountStatus;
import org.ganjp.api.auth.role.RoleRepository;
import org.ganjp.api.auth.user.UserRepository;
import org.ganjp.api.auth.role.UserRoleRepository;
import org.ganjp.api.auth.session.ActiveUserService;
import org.ganjp.api.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActiveUserService activeUserService;

    /**
     * Get all users with pagination
     *
     * @param pageable pagination information
     * @return Page of UserResponse objects
     */
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(this::mapToUserResponse);
    }
    
    /**
     * Find users by username containing the provided string
     *
     * @param username username substring to search for
     * @param pageable pagination information
     * @return Page of UserResponse objects
     */
    public Page<UserResponse> findUsersByUsernameContaining(String username, Pageable pageable) {
        return userRepository.findByUsernameContainingIgnoreCase(username, pageable)
                .map(this::mapToUserResponse);
    }
    
    /**
     * Find users by role code
     *
     * @param roleCode role code to search for
     * @param pageable pagination information
     * @return Page of UserResponse objects
     */
    public Page<UserResponse> findUsersByRoleCode(String roleCode, Pageable pageable) {
        return userRepository.findUsersByRoleCode(roleCode, pageable)
                .map(this::mapToUserResponse);
    }
    
    /**
     * Find users by role code and username containing the provided string
     *
     * @param roleCode role code to search for
     * @param username username substring to search for
     * @param pageable pagination information
     * @return Page of UserResponse objects
     */
    public Page<UserResponse> findUsersByRoleCodeAndUsernameContaining(String roleCode, String username, Pageable pageable) {
        return userRepository.findUsersByRoleCodeAndUsernameContaining(roleCode, username, pageable)
                .map(this::mapToUserResponse);
    }
    
    /**
     * Find users by nickname containing the provided string
     *
     * @param nickname nickname substring to search for
     * @param pageable pagination information
     * @return Page of UserResponse objects
     */
    public Page<UserResponse> findUsersByNicknameContaining(String nickname, Pageable pageable) {
        return userRepository.findByNicknameContainingIgnoreCase(nickname, pageable)
                .map(this::mapToUserResponse);
    }
    
    /**
     * Find users by email containing the provided string
     *
     * @param email email substring to search for
     * @param pageable pagination information
     * @return Page of UserResponse objects
     */
    public Page<UserResponse> findUsersByEmailContaining(String email, Pageable pageable) {
        return userRepository.findByEmailContainingIgnoreCase(email, pageable)
                .map(this::mapToUserResponse);
    }
    
    /**
     * Find users by mobile information
     *
     * @param mobileCountryCode mobile country code to search for
     * @param mobileNumber mobile number substring to search for
     * @param pageable pagination information
     * @return Page of UserResponse objects
     */
    public Page<UserResponse> findUsersByMobileInfo(String mobileCountryCode, String mobileNumber, Pageable pageable) {
        return userRepository.findByMobileInfo(mobileCountryCode, mobileNumber, pageable)
                .map(this::mapToUserResponse);
    }
    
    /**
     * Find users by account status
     *
     * @param accountStatus account status to search for
     * @param pageable pagination information
     * @return Page of UserResponse objects
     */
    public Page<UserResponse> findUsersByAccountStatus(AccountStatus accountStatus, Pageable pageable) {
        return userRepository.findByAccountStatus(accountStatus, pageable)
                .map(this::mapToUserResponse);
    }
    
    /**
     * Find users by active status
     *
     * @param active active status to search for
     * @param pageable pagination information
     * @return Page of UserResponse objects
     */
    public Page<UserResponse> findUsersByActive(Boolean active, Pageable pageable) {
        return userRepository.findByActive(active, pageable)
                .map(this::mapToUserResponse);
    }
    
    /**
     * Advanced search with multiple criteria
     *
     * @param username username substring (optional)
     * @param nickname nickname substring (optional)
     * @param email email substring (optional)
     * @param mobileCountryCode mobile country code (optional)
     * @param mobileNumber mobile number substring (optional)
     * @param accountStatus account status (optional)
     * @param active active status (optional)
     * @param roleCode role code (optional)
     * @param pageable pagination information
     * @return Page of UserResponse objects
     */
    public Page<UserResponse> findUsersWithCriteria(String username, String nickname, String email,
                                                   String mobileCountryCode, String mobileNumber,
                                                   AccountStatus accountStatus, Boolean active, String roleCode,
                                                   Pageable pageable) {
        return userRepository.findUsersWithCriteria(username, nickname, email, mobileCountryCode, 
                                                  mobileNumber, accountStatus, active, roleCode, pageable)
                .map(this::mapToUserResponse);
    }

    /**
     * Get a user by ID
     *
     * @param id user ID
     * @return UserResponse
     * @throws ResourceNotFoundException if user not found
     */
    public UserResponse getUserById(String id) {
        return userRepository.findById(id)
                .map(this::mapToUserResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }
    
    /**
     * Get a user by username
     *
     * @param username username to search for
     * @return UserResponse
     * @throws ResourceNotFoundException if user not found
     */
    public UserResponse getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(this::mapToUserResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    /**
     * Create a new user
     *
     * @param userUpsertRequest user data
     * @param currentUserId ID of the user performing the operation
     * @return UserResponse
     */
    @Transactional
    public UserResponse createUser(UserUpsertRequest userUpsertRequest, String currentUserId) {
        // Validate username uniqueness
        if (userRepository.existsByUsername(userUpsertRequest.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }

        // Validate email uniqueness if provided
        if (userUpsertRequest.getEmail() != null && userRepository.existsByEmail(userUpsertRequest.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        // Validate mobile uniqueness if provided
        if (userUpsertRequest.getMobileCountryCode() != null && userUpsertRequest.getMobileNumber() != null &&
                userRepository.existsByMobileCountryCodeAndMobileNumber(
                        userUpsertRequest.getMobileCountryCode(), userUpsertRequest.getMobileNumber())) {
            throw new IllegalArgumentException("Mobile number is already registered");
        }

        // Create user entity
        String userId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        User user = User.builder()
                .id(userId)
                .username(userUpsertRequest.getUsername())
                .nickname(userUpsertRequest.getNickname())
                .email(userUpsertRequest.getEmail())
                .mobileCountryCode(userUpsertRequest.getMobileCountryCode())
                .mobileNumber(userUpsertRequest.getMobileNumber())
                .accountStatus(userUpsertRequest.getAccountStatus() != null ? userUpsertRequest.getAccountStatus() : AccountStatus.pending_verification)
                .active(userUpsertRequest.getActive() != null ? userUpsertRequest.getActive() : true)
                .passwordChangedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(currentUserId)
                .updatedBy(currentUserId)
                .build();

        if (userUpsertRequest.getPassword() != null && !userUpsertRequest.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userUpsertRequest.getPassword()));
        }

        User savedUser = userRepository.save(user);

        // Assign roles if provided
        if (userUpsertRequest.getRoleCodes() != null && !userUpsertRequest.getRoleCodes().isEmpty()) {
            assignRolesToUser(savedUser, userUpsertRequest.getRoleCodes(), currentUserId);
        }

        return mapToUserResponse(savedUser);
    }

    /**
     * Update an existing user (partial update)
     * 
     * This method implements partial updates to a user resource:
     * - Only fields that are non-null in userPatchRequest will be updated
     * - Fields not included in the request will retain their current values
     * - Used for PATCH operations for partial resource updates
     *
     * @param id user ID to update
     * @param userPatchRequest updated user data for partial updates
     * @param currentUserId ID of the user performing the operation
     * @return UserResponse
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional
    public UserResponse updateUserPartially(String id, UserPatchRequest userPatchRequest, String currentUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Check username uniqueness if it's being changed
        if (userPatchRequest.getUsername() != null && !userPatchRequest.getUsername().equals(user.getUsername())
                && userRepository.existsByUsername(userPatchRequest.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }

        // Check email uniqueness if it's being changed
        if (userPatchRequest.getEmail() != null && !userPatchRequest.getEmail().equals(user.getEmail())
                && userRepository.existsByEmail(userPatchRequest.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        // Check mobile uniqueness if it's being changed
        if (userPatchRequest.getMobileCountryCode() != null && userPatchRequest.getMobileNumber() != null
                && (user.getMobileCountryCode() == null || user.getMobileNumber() == null
                || !userPatchRequest.getMobileCountryCode().equals(user.getMobileCountryCode())
                || !userPatchRequest.getMobileNumber().equals(user.getMobileNumber()))
                && userRepository.existsByMobileCountryCodeAndMobileNumber(
                userPatchRequest.getMobileCountryCode(), userPatchRequest.getMobileNumber())) {
            throw new IllegalArgumentException("Mobile number is already registered");
        }

        // Update fields if provided
        if (userPatchRequest.getUsername() != null) {
            user.setUsername(userPatchRequest.getUsername());
        }

        if (userPatchRequest.getNickname() != null) {
            user.setNickname(userPatchRequest.getNickname());
        }

        if (userPatchRequest.getEmail() != null) {
            user.setEmail(userPatchRequest.getEmail());
        }

        // Update mobile fields together
        if (userPatchRequest.getMobileCountryCode() != null && userPatchRequest.getMobileNumber() != null) {
            user.setMobileCountryCode(userPatchRequest.getMobileCountryCode());
            user.setMobileNumber(userPatchRequest.getMobileNumber());
        } else if (userPatchRequest.getMobileCountryCode() == null && userPatchRequest.getMobileNumber() == null
                   && user.getMobileCountryCode() != null && user.getMobileNumber() != null) {
            // Clear mobile fields if explicitly set to null
            user.setMobileCountryCode(null);
            user.setMobileNumber(null);
        }

        // Update password if provided
        if (userPatchRequest.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(userPatchRequest.getPassword()));
            user.setPasswordChangedAt(LocalDateTime.now());
        }

        // Update account status if provided
        if (userPatchRequest.getAccountStatus() != null) {
            user.setAccountStatus(userPatchRequest.getAccountStatus());
        }

        // Update active status if provided
        if (userPatchRequest.getActive() != null) {
            user.setActive(userPatchRequest.getActive());
        }

        // Update audit fields
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(currentUserId);

        User updatedUser = userRepository.save(user);

        // Update roles if provided
        if (userPatchRequest.getRoleCodes() != null) {
            // Remove existing roles
            List<UserRole> existingUserRoles = userRoleRepository.findByUserId(id);
            userRoleRepository.deleteAll(existingUserRoles);
            
            // Assign new roles
            if (!userPatchRequest.getRoleCodes().isEmpty()) {
                assignRolesToUser(updatedUser, userPatchRequest.getRoleCodes(), currentUserId);
            }
        }

        return mapToUserResponse(updatedUser);
    }

    /**
     * Replace an existing user (full update)
     * 
     * This method implements a complete replacement of a user resource:
     * - All fields from userUpsertRequest are used to replace current values
     * - All fields should be provided in the request (null values will clear existing values)
     * - Used for PUT operations that replace the entire resource
     *
     * @param id User ID to replace
     * @param userUpsertRequest User data for complete replacement 
     * @param currentUserId ID of the user making this request
     * @return Updated user details
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional
    public UserResponse updateUserFully(String id, UserUpsertRequest userUpsertRequest, String currentUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Check username uniqueness if it's being changed
        if (!userUpsertRequest.getUsername().equals(user.getUsername())
                && userRepository.existsByUsername(userUpsertRequest.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }

        // Check email uniqueness if it's being changed
        if (!userUpsertRequest.getEmail().equals(user.getEmail())
                && userRepository.existsByEmail(userUpsertRequest.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        // Check mobile uniqueness if it's being changed
        if (userUpsertRequest.getMobileCountryCode() != null && userUpsertRequest.getMobileNumber() != null
                && !userUpsertRequest.getMobileCountryCode().equals(user.getMobileCountryCode())
                && !userUpsertRequest.getMobileNumber().equals(user.getMobileNumber())
                && userRepository.existsByMobileCountryCodeAndMobileNumber(
                    userUpsertRequest.getMobileCountryCode(), userUpsertRequest.getMobileNumber())) {
            throw new IllegalArgumentException("Mobile number is already registered");
        }

        // Update user fields for complete replacement
        user.setUsername(userUpsertRequest.getUsername());
        user.setNickname(userUpsertRequest.getNickname());
        user.setEmail(userUpsertRequest.getEmail());
        
        // Only update password if provided
        if (userUpsertRequest.getPassword() != null && !userUpsertRequest.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userUpsertRequest.getPassword()));
        }
        
        user.setMobileCountryCode(userUpsertRequest.getMobileCountryCode());
        user.setMobileNumber(userUpsertRequest.getMobileNumber());
        user.setAccountStatus(userUpsertRequest.getAccountStatus());
        user.setActive(userUpsertRequest.getActive());
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(currentUserId);
        
        // Save user
        User updatedUser = userRepository.save(user);
        
        // Update roles if provided
        if (userUpsertRequest.getRoleCodes() != null && !userUpsertRequest.getRoleCodes().isEmpty()) {
            // Remove existing roles
            List<UserRole> existingUserRoles = userRoleRepository.findByUserId(id);
            userRoleRepository.deleteAll(existingUserRoles);
            
            // Add new roles
            List<UserRole> userRoles = new ArrayList<>();
            for (String roleCode : userUpsertRequest.getRoleCodes()) {
                Role role = roleRepository.findByCode(roleCode)
                        .orElseThrow(() -> new ResourceNotFoundException("Role", "code", roleCode));
                
                LocalDateTime now = LocalDateTime.now();
                UserRole userRole = UserRole.builder()
                        .user(updatedUser)
                        .role(role)
                        .grantedAt(now)
                        .grantedBy(currentUserId)
                        .createdAt(now)
                        .updatedAt(now)
                        .createdBy(currentUserId)
                        .updatedBy(currentUserId)
                        .active(true)
                        .build();
                
                userRoles.add(userRole);
            }
            
            userRoleRepository.saveAll(userRoles);
        }
        
        // Get updated user with roles
        return mapToUserResponse(updatedUser);
    }
    
    /**
     * Delete a user by ID (soft delete)
     *
     * @param id user ID
     * @param currentUserId ID of the user performing the operation
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional
    public void deleteUser(String id, String currentUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Perform soft delete
        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(currentUserId);

        userRepository.save(user);
    }

    /**
     * Hard delete a user by ID (use with caution)
     *
     * @param id user ID
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional
    public void hardDeleteUser(String id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User", "id", id);
        }
        userRepository.deleteById(id);
    }

    /**
     * Change a user's password
     *
     * @param id user ID
     * @param newPassword new password
     * @param currentUserId ID of the user performing the operation
     * @return UserResponse
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional
    public UserResponse changePassword(String id, String newPassword, String currentUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(currentUserId);

        User updatedUser = userRepository.save(user);
        return mapToUserResponse(updatedUser);
    }

    /**
     * Toggle a user's active status
     *
     * @param id user ID
     * @param currentUserId ID of the user performing the operation
     * @return UserResponse
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional
    public UserResponse toggleUserActiveStatus(String id, String currentUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        user.setActive(!user.isActive());
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(currentUserId);

        User updatedUser = userRepository.save(user);
        return mapToUserResponse(updatedUser);
    }

    /**
     * Assign roles to a user
     *
     * @param user User entity
     * @param roleCodes Set of role codes
     * @param currentUserId ID of the user performing the operation
     */
    private void assignRolesToUser(User user, Set<String> roleCodes, String currentUserId) {
        List<Role> roles = new ArrayList<>();
        for (String code : roleCodes) {
            roleRepository.findByCode(code).ifPresent(roles::add);
        }
        
        // Validate all requested roles exist
        if (roles.size() != roleCodes.size()) {
            throw new IllegalArgumentException("One or more role codes are invalid");
        }

        LocalDateTime now = LocalDateTime.now();

        List<UserRole> userRoles = roles.stream().map(role -> {
            return UserRole.builder()
                    .user(user)
                    .role(role)
                    .grantedAt(now)
                    .grantedBy(currentUserId)
                    .createdAt(now)
                    .updatedAt(now)
                    .createdBy(currentUserId)
                    .updatedBy(currentUserId)
                    .active(true)
                    .build();
        }).collect(Collectors.toList());

        userRoleRepository.saveAll(userRoles);
    }

    /**
     * Map User entity to UserResponse DTO
     *
     * @param user User entity
     * @return UserResponse
     */
    private UserResponse mapToUserResponse(User user) {
        // Explicitly fetch user roles from the repository since they're marked as @Transient in User entity
        List<UserRole> userRoles = userRoleRepository.findByUserId(user.getId());
        
        List<RoleResponse> roleResponses = userRoles.stream()
                .filter(ur -> ur.isActive() && (ur.getExpiresAt() == null || ur.getExpiresAt().isAfter(LocalDateTime.now())))
                .map(ur -> {
                    Role role = ur.getRole();
                    return RoleResponse.builder()
                            .id(role.getId())
                            .code(role.getCode())
                            .name(role.getName())
                            .description(role.getDescription())
                            .sortOrder(role.getSortOrder())
                            .level(role.getLevel())
                            .parentRoleId(role.getParentRole() != null ? role.getParentRole().getId() : null)
                            .active(role.isActive())
                            .systemRole(role.isSystemRole())
                            .createdAt(role.getCreatedAt())
                            .updatedAt(role.getUpdatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .mobileCountryCode(user.getMobileCountryCode())
                .mobileNumber(user.getMobileNumber())
                .accountStatus(user.getAccountStatus())
                .active(user.isActive())
                .lastLoginAt(user.getLastLoginAt())
                .lastLoginIp(user.getLastLoginIp())
                .passwordChangedAt(user.getPasswordChangedAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .roles(roleResponses)
                .build();
    }

    /**
     * Get dashboard statistics for admin users
     * 
     * This method provides comprehensive user statistics including:
     * - Total number of users
     * - Number of active users (active = true)
     * - Number of locked users (account_status = 'locked')
     * - Number of suspended users (account_status = 'suspended')
     * - Number of pending verification users (account_status = 'pending_verification')
     * - Number of active sessions (placeholder - requires session management)
     * 
     * @return Map containing all dashboard statistics
     */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Total number of users
        long totalUsers = userRepository.count();
        stats.put("totalUsers", totalUsers);
        
        // Number of active users (active = true)
        long activeUsers = userRepository.countByActiveTrue();
        stats.put("activeUsers", activeUsers);
        
        // Number of locked users
        long lockedUsers = userRepository.countByAccountStatus(AccountStatus.locked);
        stats.put("lockedUsers", lockedUsers);
        
        // Number of suspended users
        long suspendedUsers = userRepository.countByAccountStatus(AccountStatus.suspended);
        stats.put("suspendedUsers", suspendedUsers);
        
        // Number of pending verification users
        long pendingVerificationUsers = userRepository.countByAccountStatus(AccountStatus.pending_verification);
        stats.put("pendingVerificationUsers", pendingVerificationUsers);
        
        // Active sessions - real count from memory
        long activeSessions = activeUserService.getActiveUserCount();
        stats.put("activeSessions", activeSessions);
        
        return stats;
    }
}
