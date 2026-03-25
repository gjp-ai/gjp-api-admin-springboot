package org.ganjp.api.auth.role;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ganjp.api.auth.security.JwtUtils;
import org.ganjp.api.common.model.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final JwtUtils jwtUtils;
    
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        List<RoleResponse> roles = roleService.getAllRoles();
        return ResponseEntity.ok(ApiResponse.success(roles, "Roles retrieved successfully"));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getActiveRoles() {
        List<RoleResponse> roles = roleService.getActiveRoles();
        return ResponseEntity.ok(ApiResponse.success(roles, "Active roles retrieved successfully"));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable String id) {
        RoleResponse role = roleService.getRoleById(id);
        return ResponseEntity.ok(ApiResponse.success(role, "Role retrieved successfully"));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleByCode(@PathVariable String code) {
        RoleResponse role = roleService.getRoleByCode(code);
        return ResponseEntity.ok(ApiResponse.success(role, "Role retrieved successfully"));
    }

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
     * Partially updates a role with only the fields provided in the request.
     * This allows for updating individual fields without needing to send the entire role object.
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

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @PathVariable String id,
            HttpServletRequest request) {
        String userId = jwtUtils.extractUserIdFromToken(request);
        roleService.deleteRole(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Role deleted successfully"));
    }

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