package org.ganjp.api.auth.role;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entity representing a role in the authentication system.
 * Roles are used to assign permissions to users.
 */
@Getter
@Setter
@ToString(exclude = {"parentRole", "childRoles"}) // Exclude bidirectional relationships to prevent circular references
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auth_roles", uniqueConstraints = {
        @UniqueConstraint(columnNames = "code", name = "uk_role_code")
})
public class Role {
    /**
     * Unique identifier for the role.
     */
    @Id
    @Column(columnDefinition = "CHAR(36)")
    private String id;
    
    /**
     * Unique code representing the role (e.g., "ADMIN", "USER").
     * Used in security contexts and for role identification.
     */
    @Column(name = "code", length = 50, nullable = false)
    private String code;
    
    /**
     * Human-readable name for the role.
     */
    @Column(name = "name", length = 100, nullable = false)
    private String name;
    
    /**
     * Optional description providing details about the role and its permissions.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    /**
     * Order for displaying roles in UI (lower numbers appear first)
     */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
    
    /**
     * Parent role in the role hierarchy.
     * Supports hierarchical role structures.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_role_id", columnDefinition = "CHAR(36)")
    private Role parentRole;
    
    /**
     * Child roles that inherit from this role.
     * Supports hierarchical role structures.
     */
    @OneToMany(mappedBy = "parentRole", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Role> childRoles = new ArrayList<>();
    
    /**
     * Hierarchical level of this role (0 is top level)
     */
    @Column(name = "level")
    @Builder.Default
    private int level = 0;
    
    /**
     * Flag indicating if this is a system-defined role that should not be modified.
     */
    @Column(name = "is_system_role")
    @Builder.Default
    private boolean systemRole = false;
    
    /**
     * Timestamp when the role was created.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when the role was last updated.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * ID of the user who created this role.
     */
    @Column(name = "created_by", columnDefinition = "CHAR(36)")
    private String createdBy;
    
    /**
     * ID of the user who last updated this role.
     */
    @Column(name = "updated_by", columnDefinition = "CHAR(36)")
    private String updatedBy;
    
    /**
     * Flag indicating if the role is currently active.
     */
    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return id != null && Objects.equals(id, role.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}