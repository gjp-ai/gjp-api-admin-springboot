package org.ganjp.api.auth.user;

import org.ganjp.api.auth.user.User;
import org.ganjp.api.auth.user.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
    
    Page<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByMobileCountryCodeAndMobileNumber(String mobileCountryCode, String mobileNumber);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);

    boolean existsByMobileCountryCodeAndMobileNumber(String mobileCountryCode, String mobileNumber);
    
    List<User> findByAccountStatus(AccountStatus accountStatus);
    
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :now, u.lastLoginIp = :ip WHERE u.id = :userId")
    void updateLoginSuccess(@Param("userId") String userId, @Param("now") LocalDateTime now, @Param("ip") String ip);

    // get username by email or mobile country code + mobile number
    @Query("SELECT u.username FROM User u WHERE u.email = :email OR (u.mobileCountryCode = :mobileCountryCode AND u.mobileNumber = :mobileNumber)")
    Optional<String> findUsernameByEmailOrMobile(@Param("email") String email,
                                                  @Param("mobileCountryCode") String mobileCountryCode,
                                                  @Param("mobileNumber") String mobileNumber);
    
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = "UPDATE auth_users SET failed_login_attempts = COALESCE(failed_login_attempts, 0) + 1, last_failed_login_at = :now WHERE id = :userId", nativeQuery = true)
    int updateLoginFailureByIdNative(@Param("userId") String userId, @Param("now") LocalDateTime now);

    @Query("SELECT u FROM User u WHERE u.accountLockedUntil IS NOT NULL AND u.accountLockedUntil <= :now")
    List<User> findUsersWithExpiredLocks(@Param("now") LocalDateTime now);
    
    @Query("SELECT u FROM User u WHERE u.passwordChangedAt IS NOT NULL AND u.passwordChangedAt <= :now")
    List<User> findUsersWithOldPasswords(@Param("now") LocalDateTime now);
    
    /**
     * Find users who have a specific role assigned
     * @param roleCode The role code to search for
     * @param pageable Pagination information
     * @return Page of users with the specified role
     */
    @Query("SELECT DISTINCT u FROM User u " +
           "JOIN UserRole ur ON u.id = ur.user.id " +
           "JOIN Role r ON ur.role.id = r.id " +
           "WHERE r.code = :roleCode " +
           "AND ur.active = true " +
           "AND (ur.expiresAt IS NULL OR ur.expiresAt > CURRENT_TIMESTAMP)")
    Page<User> findUsersByRoleCode(@Param("roleCode") String roleCode, Pageable pageable);
    
    /**
     * Find users who have a specific role assigned and username contains the search term
     * @param roleCode The role code to search for
     * @param username Username substring to search for
     * @param pageable Pagination information
     * @return Page of users with the specified role and matching username
     */
    @Query("SELECT DISTINCT u FROM User u " +
           "JOIN UserRole ur ON u.id = ur.user.id " +
           "JOIN Role r ON ur.role.id = r.id " +
           "WHERE r.code = :roleCode " +
           "AND ur.active = true " +
           "AND (ur.expiresAt IS NULL OR ur.expiresAt > CURRENT_TIMESTAMP) " +
           "AND LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))")
    Page<User> findUsersByRoleCodeAndUsernameContaining(@Param("roleCode") String roleCode, 
                                                        @Param("username") String username, 
                                                        Pageable pageable);

    /**
     * Find users by nickname containing the search term
     * @param nickname Nickname substring to search for
     * @param pageable Pagination information
     * @return Page of users with matching nickname
     */
    Page<User> findByNicknameContainingIgnoreCase(String nickname, Pageable pageable);

    /**
     * Find users by email containing the search term
     * @param email Email substring to search for
     * @param pageable Pagination information
     * @return Page of users with matching email
     */
    Page<User> findByEmailContainingIgnoreCase(String email, Pageable pageable);

    /**
     * Find users by mobile country code and mobile number
     * @param mobileCountryCode Mobile country code to search for
     * @param mobileNumber Mobile number substring to search for
     * @param pageable Pagination information
     * @return Page of users with matching mobile information
     */
    @Query("SELECT u FROM User u WHERE " +
           "(:mobileCountryCode IS NULL OR u.mobileCountryCode = :mobileCountryCode) " +
           "AND (:mobileNumber IS NULL OR LOWER(u.mobileNumber) LIKE LOWER(CONCAT('%', :mobileNumber, '%')))")
    Page<User> findByMobileInfo(@Param("mobileCountryCode") String mobileCountryCode,
                                @Param("mobileNumber") String mobileNumber,
                                Pageable pageable);

    /**
     * Find users by account status
     * @param accountStatus Account status to search for
     * @param pageable Pagination information
     * @return Page of users with matching account status
     */
    Page<User> findByAccountStatus(AccountStatus accountStatus, Pageable pageable);

    /**
     * Find users by active status
     * @param active Active status to search for
     * @param pageable Pagination information
     * @return Page of users with matching active status
     */
    Page<User> findByActive(Boolean active, Pageable pageable);

    /**
     * Advanced search with multiple criteria
     * @param username Username substring (optional)
     * @param nickname Nickname substring (optional)
     * @param email Email substring (optional)
     * @param mobileCountryCode Mobile country code (optional)
     * @param mobileNumber Mobile number substring (optional)
     * @param accountStatus Account status (optional)
     * @param active Active status (optional)
     * @param roleCode Role code (optional)
     * @param pageable Pagination information
     * @return Page of users matching the criteria
     */
    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN UserRole ur ON u.id = ur.user.id " +
           "LEFT JOIN Role r ON ur.role.id = r.id " +
           "WHERE (:username IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))) " +
           "AND (:nickname IS NULL OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :nickname, '%'))) " +
           "AND (:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))) " +
           "AND (:mobileCountryCode IS NULL OR u.mobileCountryCode = :mobileCountryCode) " +
           "AND (:mobileNumber IS NULL OR LOWER(u.mobileNumber) LIKE LOWER(CONCAT('%', :mobileNumber, '%'))) " +
           "AND (:accountStatus IS NULL OR u.accountStatus = :accountStatus) " +
           "AND (:active IS NULL OR u.active = :active) " +
           "AND (:roleCode IS NULL OR (r.code = :roleCode AND ur.active = true AND (ur.expiresAt IS NULL OR ur.expiresAt > CURRENT_TIMESTAMP)))")
    Page<User> findUsersWithCriteria(@Param("username") String username,
                                     @Param("nickname") String nickname,
                                     @Param("email") String email,
                                     @Param("mobileCountryCode") String mobileCountryCode,
                                     @Param("mobileNumber") String mobileNumber,
                                     @Param("accountStatus") AccountStatus accountStatus,
                                     @Param("active") Boolean active,
                                     @Param("roleCode") String roleCode,
                                     Pageable pageable);
    
    // Dashboard statistics methods
    long countByActiveTrue();
    
    long countByAccountStatus(AccountStatus accountStatus);
}