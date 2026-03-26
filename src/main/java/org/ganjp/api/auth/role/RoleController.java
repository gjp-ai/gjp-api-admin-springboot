package org.ganjp.api.auth.role;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.security.JwtUtils;
import org.ganjp.api.common.model.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for role management operations.
 * Provides CRUD endpoints for managing roles in the RBAC system.
 */
@Slf4j
@RestController
@RequestMapping("/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final JwtUtils jwtUtils;
    
    /**
     * Retrieve all roles sorted by sort order and name.
     *
     * @return list of all roles
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        List<RoleResponse> roles = roleService.getAllRoles();
        return ResponseEntity.ok(ApiResponse.success(roles, "Roles retrieved successfully"));
    }

    /**
     * Retrieve only active roles.
     *
     * @return list of active roles
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getActiveRoles() {
        List<RoleResponse> roles = roleService.getActiveRoles();
        return ResponseEntity.ok(ApiResponse.success(roles, "Active roles retrieved successfully"));
    }

    /**
     * Retrieve a role by its ID.
     *
     * @param id role UUID
     * @return role details
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable String id) {
        RoleResponse role = roleService.getRoleById(id);
        return ResponseEntity.ok(ApiResponse.success(role, "Role retrieved successfully"));
    }

    /**
     * Retrieve a role by its unique code.
     *
     * @param code role code (e.g., ADMIN, USER)
     * @return role details
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleByCode(@PathVariable String code) {
        RoleResponse role = roleService.getRoleByCode(code);
        return ResponseEntity.ok(ApiResponse.success(role, "Role retrieved successfully"));
    }

    /**
     * Create a new role.
     *
     * @param roleRequest role creation data
     * @param request HTTP request for extracting the current user
     * @return created role details
     */
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody RoleCreateRequest roleRequest,
            HttpServletRequest request) {
        String userId = jwtUtils.extractUserIdFromToken(request);
        RoleResponse createdRole = roleService.createRole(roleRequest, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdRole, "Role created successfully"));
    }

    /**
     * Replace a role entirely (full update).
     *
     * @param id role UUID
     * @param roleRequest role data for complete replacement
     * @param request HTTP request for extracting the current user
     * @return updated role details
     */
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable String id,
            @Valid @RequestBody RoleCreateRequest roleRequest,
            HttpServletRequest request) {
        String userId = jwtUtils.extractUserIdFromToken(request);
        RoleResponse updatedRole = roleService.updateRoleFully(id, roleRequest, userId);
        return ResponseEntity.ok(ApiResponse.success(updatedRole, "Role updated successfully"));
    }
    
    /**
     * Partially update a role with only the fields provided in the request.
     *
     * @param id role UUID
     * @param partiallyUpdateRequest partial role data
     * @param request HTTP request for extracting the current user
     * @return updated role details
     */
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRolePartially(
            @PathVariable String id,
            @Valid @RequestBody RoleUpdateRequest partiallyUpdateRequest,
            HttpServletRequest request) {
        String userId = jwtUtils.extractUserIdFromToken(request);
        RoleResponse updatedRole = roleService.updateRolePartially(id, partiallyUpdateRequest, userId);
        return ResponseEntity.ok(ApiResponse.success(updatedRole, "Role updated successfully"));
    }

    /**
     * Delete a role by ID. System roles and roles assigned to users cannot be deleted.
     *
     * @param id role UUID
     * @param request HTTP request for extracting the current user
     * @return success message
     */
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @PathVariable String id,
            HttpServletRequest request) {
        String userId = jwtUtils.extractUserIdFromToken(request);
        roleService.deleteRole(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Role deleted successfully"));
    }

    /**
     * Toggle a role's active status.
     *
     * @param id role UUID
     * @param request HTTP request for extracting the current user
     * @return updated role details
     */
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<ApiResponse<RoleResponse>> toggleRoleStatus(
            @PathVariable String id,
            HttpServletRequest request) {
        String userId = jwtUtils.extractUserIdFromToken(request);
        RoleResponse updatedRole = roleService.toggleRoleStatus(id, userId);
        return ResponseEntity.ok(ApiResponse.success(updatedRole, "Role status toggled successfully"));
    }
}