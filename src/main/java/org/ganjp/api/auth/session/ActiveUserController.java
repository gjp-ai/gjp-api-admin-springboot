package org.ganjp.api.auth.session;

import lombok.RequiredArgsConstructor;
import org.ganjp.api.common.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for managing active user sessions
 * Provides endpoints for administrators to monitor active user sessions
 */
@RestController
@RequestMapping("/v1/admin/sessions")
@RequiredArgsConstructor
public class ActiveUserController {

    private final ActiveUserService activeUserService;

    /**
     * Get statistics about active user sessions
     * 
     * @return Active session statistics
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getActiveSessionStats() {
        Map<String, Object> stats = Map.of(
            "activeSessionCount", activeUserService.getActiveUserCount(),
            "sessionTimeoutMinutes", activeUserService.getSessionTimeoutMinutes()
        );
        
        return ResponseEntity.ok(ApiResponse.success(stats, "Active session statistics retrieved successfully"));
    }

    /**
     * Get details of all active user sessions
     * 
     * @return Map of active users with their session information
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<Map<String, ActiveUserService.SessionInfo>>> getActiveUsers() {
        Map<String, ActiveUserService.SessionInfo> activeUsers = activeUserService.getAllActiveUsers();
        
        return ResponseEntity.ok(ApiResponse.success(activeUsers, "Active user sessions retrieved successfully"));
    }

    /**
     * Force cleanup of expired sessions
     * 
     * @return Cleanup results
     */
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @PostMapping("/cleanup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> forceCleanup() {
        long beforeCount = activeUserService.getActiveUserCount();
        activeUserService.forceCleanup();
        long afterCount = activeUserService.getActiveUserCount();
        
        Map<String, Object> result = Map.of(
            "sessionsBeforeCleanup", beforeCount,
            "sessionsAfterCleanup", afterCount,
            "sessionsRemoved", beforeCount - afterCount
        );
        
        return ResponseEntity.ok(ApiResponse.success(result, "Session cleanup completed successfully"));
    }
}
