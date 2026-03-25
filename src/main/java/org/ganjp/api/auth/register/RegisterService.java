package org.ganjp.api.auth.register;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.role.Role;
import org.ganjp.api.auth.user.User;
import org.ganjp.api.auth.role.UserRole;
import org.ganjp.api.auth.user.AccountStatus;
import org.ganjp.api.auth.role.RoleRepository;
import org.ganjp.api.auth.user.UserRepository;
import org.ganjp.api.auth.role.UserRoleRepository;
import org.ganjp.api.common.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Register a new user with ROLE_USER role
     *
     * @param registerRequest The registration request data
     * @return The created user data
     */
    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        // Check if username already exists
        if (registerRequest.getUsername() != null && userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }

        // Check if email already exists
        if (registerRequest.getEmail() != null && userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        // Check if mobile number already exists
        if (registerRequest.getMobileCountryCode() != null && registerRequest.getMobileNumber() != null &&
            userRepository.existsByMobileCountryCodeAndMobileNumber(
                registerRequest.getMobileCountryCode(), registerRequest.getMobileNumber())) {
            throw new IllegalArgumentException("Mobile number is already registered");
        }

        // Get the USER role
        Role userRole = roleRepository.findByCode("USER")
                .orElseThrow(() -> new ResourceNotFoundException("Role", "code", "USER"));

        // Validate that username is provided (now required field)
        if (registerRequest.getUsername() == null || registerRequest.getUsername().isEmpty()) {
            if (registerRequest.getEmail() != null && !registerRequest.getEmail().isEmpty()) {
                registerRequest.setUsername(registerRequest.getEmail().split("@")[0]); // Use email prefix as username if not provided
            } else if (registerRequest.getMobileCountryCode() != null && registerRequest.getMobileNumber() != null) {
                registerRequest.setUsername(registerRequest.getMobileCountryCode() + registerRequest.getMobileNumber()); // Use mobile as username
            } else {
                throw new IllegalArgumentException("Username is required and must be provided");
            }
        }

        // Create new user account with all fields explicitly set to avoid missing defaults
        String uuid = UUID.randomUUID().toString();
        User user = User.builder()
                .id(uuid)
                .username(registerRequest.getUsername())
                .email(registerRequest.getEmail())
                .mobileCountryCode(registerRequest.getMobileCountryCode())
                .mobileNumber(registerRequest.getMobileNumber())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .nickname(registerRequest.getNickname())
                .accountStatus(AccountStatus.pending_verification)
                .passwordChangedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(uuid)
                .updatedBy(uuid)
                .build();

        User savedUser = userRepository.save(user);

        // Create user role relationship
        UserRole userRoleEntity = UserRole.builder()
                .user(savedUser)
                .role(userRole)
                .grantedAt(LocalDateTime.now())
                .grantedBy(uuid)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(uuid)
                .updatedBy(uuid)
                .active(true)
                .build();

        userRoleRepository.save(userRoleEntity);

        return RegisterResponse.builder()
                .id(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .mobileCountryCode(savedUser.getMobileCountryCode())
                .mobileNumber(savedUser.getMobileNumber())
                .nickname(savedUser.getNickname())
                .accountStatus(savedUser.getAccountStatus())
                .active(savedUser.isActive())
                .build();
    }
}
