package org.ganjp.api.auth.role;

import org.ganjp.api.auth.role.Role;
import org.ganjp.api.auth.user.User;
import org.ganjp.api.auth.role.UserRole;
import org.ganjp.api.auth.role.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    
    /**
     * Find all user roles for a specific user
     * @param userId The user ID
     * @return List of UserRole objects for the specified user
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.user.id = :userId")
    List<UserRole> findByUserId(@Param("userId") String userId);
    
    List<UserRole> findByUserAndActiveTrue(User user);
    
    List<UserRole> findByRoleAndActiveTrue(Role role);
    
    Optional<UserRole> findByUserAndRole(User user, Role role);
    
    boolean existsByUserAndRole(User user, Role role);
    
    @Query("SELECT ur FROM UserRole ur WHERE ur.user = :user AND ur.active = true AND (ur.expiresAt IS NULL OR ur.expiresAt > :now)")
    List<UserRole> findActiveUserRoles(@Param("user") User user, @Param("now") LocalDateTime now);
    
    @Query("SELECT ur FROM UserRole ur WHERE ur.expiresAt IS NOT NULL AND ur.expiresAt <= :now AND ur.active = true")
    List<UserRole> findExpiredUserRoles(@Param("now") LocalDateTime now);
    
    @Query("SELECT COUNT(ur) FROM UserRole ur WHERE ur.role = :role AND ur.active = true AND (ur.expiresAt IS NULL OR ur.expiresAt > CURRENT_TIMESTAMP)")
    long countActiveUsersWithRole(@Param("role") Role role);
}
