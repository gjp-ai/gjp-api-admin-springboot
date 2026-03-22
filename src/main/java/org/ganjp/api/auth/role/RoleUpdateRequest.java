package org.ganjp.api.auth.role;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for partial updates to Role entity.
 * All fields are nullable to allow partial updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleUpdateRequest {

    @Size(min = 3, max = 30, message = "Role code must be between 3 and 30 characters")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Role code must contain only uppercase letters, numbers, and underscores")
    private String code;

    @Size(min = 3, max = 50, message = "Role name must be between 3 and 50 characters")
    private String name;

    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    private Integer sortOrder;

    /**
     * ID of the parent role in the role hierarchy, if any.
     * Can be null for top-level roles.
     */
    private String parentRoleId;

    /**
     * Hierarchical level of this role (0 is top level).
     * This will be calculated automatically if parentRoleId is provided.
     */
    private Integer level;

    private Boolean active;
}
