package org.ganjp.api.auth.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.auth.role.Role;
import org.ganjp.api.auth.role.UserRole;
import org.ganjp.api.auth.refresh.RefreshToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auth_users")
public class User implements UserDetails {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private String id;
    
    @Column(name = "nickname", length = 30)
    private String nickname;
    
    // Login credentials
    @Column(length = 30, unique = true, nullable = false)
    @jakarta.validation.constraints.NotNull(message = "Username is required")
    private String username;
    
    @Column(length = 128, unique = true)
    private String email;

    @Column(name = "mobile_country_code", length = 5)
    private String mobileCountryCode;

    @Column(name = "mobile_number", length = 15)
    private String mobileNumber;
    
    @Column(name = "password_hash", nullable = false)
    private String password;
    
    // Account status management
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.pending_verification;
    
    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;

    // Login tracking
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;
    
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;
    
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;
    
    @Column(name = "last_failed_login_at")
    private LocalDateTime lastFailedLoginAt;
    
    // Audit fields
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by", columnDefinition = "CHAR(36)")
    private String createdBy;
    
    @Column(name = "updated_by", columnDefinition = "CHAR(36)")
    private String updatedBy;
    
    // Soft delete
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Transient // Need to mark as transient since UserRole is not serializable
    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @Builder.Default
    private List<UserRole> userRoles = new ArrayList<>();
    
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<RefreshToken> refreshTokens = new ArrayList<>();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (userRoles != null) {
            for (UserRole userRole : userRoles) {
                if (userRole.isActive() && (userRole.getExpiresAt() == null || userRole.getExpiresAt().isAfter(LocalDateTime.now()))) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + userRole.getRole().getCode()));
                }
            }
        }
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountStatus != AccountStatus.suspended && active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountStatus != AccountStatus.locked 
            && (accountLockedUntil == null || accountLockedUntil.isBefore(LocalDateTime.now()));
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // Check if we need to force a password reset
        // passwordChangedAt should not be null as it has a default value in DB
        return passwordChangedAt != null;
    }

    @Override
    public boolean isEnabled() {
        return accountStatus == AccountStatus.active && active;
    }
    
    /**
     * Get active roles (non-expired and active)
     */
    public List<Role> getRoles() {
        if (userRoles == null) {
            return new ArrayList<>();
        }
        return userRoles.stream()
                .filter(ur -> ur.isActive() && (ur.getExpiresAt() == null || ur.getExpiresAt().isAfter(LocalDateTime.now())))
                .map(UserRole::getRole)
                .toList();
    }
    
    /**
     * Check if user is active (active flag and specific account status)
     */
    public boolean isActive() {
        return active && accountStatus == AccountStatus.active;
    }

    /**
     * Builder methods for compatibility with tests
     */
    public static class UserBuilder {
        public UserBuilder passwordLastChangedAt(LocalDateTime changedAt) {
            this.passwordChangedAt = changedAt;
            return this;
        }
    }
    
    /**
     * Validates if username is valid and checks optional contact methods.
     * This implements the chk_contact_required database constraint in Java code.
     * 
     * @return true if username is valid and optional contact methods are valid when provided
     */
    @jakarta.validation.constraints.AssertTrue(message = "Username must be provided and valid")
    public boolean isLoginIdentityValid() {
        boolean isUsernameValid = username != null && username.matches("^[A-Za-z0-9._-]{3,30}$");
        boolean isEmailValid = email == null || email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
        boolean isMobileValid = (mobileCountryCode == null && mobileNumber == null) || 
                (mobileCountryCode != null && mobileNumber != null
                && mobileCountryCode.matches("^[1-9]\\d{0,3}$")
                && mobileNumber.matches("^\\d{4,15}$"));

        return isUsernameValid && isEmailValid && isMobileValid;
    }
    
    /**
     * Validates username format according to database constraint
     * 
     * @return true if username matches the regex pattern
     */
    public boolean isUsernameValid() {
        // Username is now required, so it must exist and match the pattern
        return username != null && username.matches("^[A-Za-z0-9._-]{3,30}$");
    }
    
    /**
     * Validates email format according to database constraint
     * 
     * @return true if email is null or matches the regex pattern
     */
    public boolean isEmailValid() {
        return email == null || email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
    
    /**
     * Validates mobile country code format according to database constraint
     * 
     * @return true if mobile country code is null or matches the regex pattern
     */
    public boolean isMobileCountryCodeValid() {
        return mobileCountryCode == null || mobileCountryCode.matches("^[1-9]\\d{0,3}$");
    }
    
    /**
     * Validates mobile number format according to database constraint
     * 
     * @return true if mobile number is null or matches the regex pattern
     */
    public boolean isMobileNumberValid() {
        return mobileNumber == null || mobileNumber.matches("^\\d{4,15}$");
    }
}