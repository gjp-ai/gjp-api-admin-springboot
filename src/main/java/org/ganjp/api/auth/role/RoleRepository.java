package org.ganjp.api.auth.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {
    Optional<Role> findByCode(String code);
    
    List<Role> findByActiveTrue();
    
    boolean existsByCode(String code);
    
    // Hierarchical role queries
    List<Role> findByParentRoleIsNull();
    
    List<Role> findByParentRole(Role parentRole);
    
    List<Role> findByLevel(int level);
    
    List<Role> findBySystemRole(boolean systemRole);
    
    @Query("SELECT r FROM Role r WHERE r.parentRole IS NULL ORDER BY r.sortOrder, r.name")
    List<Role> findRootRolesOrdered();
    
    @Query("SELECT r FROM Role r WHERE r.parentRole = :parent ORDER BY r.sortOrder, r.name")
    List<Role> findChildRolesOrdered(@Param("parent") Role parent);
    
    @Query("SELECT r FROM Role r WHERE r.level <= :maxLevel ORDER BY r.level, r.sortOrder, r.name")
    List<Role> findRolesByMaxLevel(@Param("maxLevel") int maxLevel);
    
    @Query("SELECT COUNT(r) FROM Role r WHERE r.parentRole = :parent AND r.active = true")
    long countActiveChildRoles(@Param("parent") Role parent);
}