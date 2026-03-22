package org.ganjp.api.auth.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) for returning role information to clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {
    /** Unique identifier for the role */
    private String id;
    
    /** Unique code representing the role (e.g., "ADMIN", "USER") */
    private String code;
    
    /** Human-readable name of the role */
    private String name;
    
    /** Optional description providing details about the role's purpose and permissions */
    private String description;
    
    /** Order for displaying roles in UI (lower numbers appear first) */
    private Integer sortOrder;
    
    /** Hierarchical level of this role (0 is top level) */
    private Integer level;
    
    /** ID of the parent role in the role hierarchy */
    private String parentRoleId;
    
    /** Flag indicating if this is a system-defined role that should not be modified */
    private Boolean systemRole;
    
    /** Flag indicating if the role is currently active */
    private Boolean active;
    
    /** Timestamp when the role was created */
    private LocalDateTime createdAt;
    
    /** Timestamp when the role was last updated */
    private LocalDateTime updatedAt;
    
    /** ID of the user who created this role */
    private String createdBy;
    
    /** ID of the user who last updated this role */
    private String updatedBy;
}