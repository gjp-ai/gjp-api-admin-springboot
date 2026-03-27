package org.ganjp.api.cms.website;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.security.JwtUtils;

import org.ganjp.api.cms.website.WebsiteCreateRequest;
import org.ganjp.api.cms.website.WebsiteUpdateRequest;
import org.ganjp.api.cms.website.WebsiteResponse;
import org.ganjp.api.cms.website.Website;
import org.ganjp.api.cms.website.WebsiteService;
import org.ganjp.api.common.model.ApiResponse;
import org.ganjp.api.common.model.PaginatedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Website management
 */
@Slf4j
@RestController
@RequestMapping("/v1/websites")
@RequiredArgsConstructor
public class WebsiteController {

    private final WebsiteService websiteService;
    private final JwtUtils jwtUtils;

    /**
     * Get all websites with pagination and filtering
     */
    /**
     * Flexible search websites by name, language, tags, and status
     * GET /v1/websites?name=xxx&lang=EN&tags=yyy&isActive=true&page=0&size=20&sort=updatedAt&direction=desc
     * 
     * @param page Page number (0-based)
     * @param size Page size
     * @param sort Sort field (e.g., updatedAt, createdAt, name, displayOrder)
     * @param direction Sort direction (asc or desc)
     * @param name Optional name filter
     * @param lang Optional language filter
     * @param tags Optional tags filter
     * @param isActive Optional active status filter
     * @return Paginated list of websites
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<WebsiteResponse>>> getWebsites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Website.Language lang,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) Boolean isActive
    ) {
        try {
            Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC : Sort.Direction.ASC;

            Pageable pageable = PageRequest.of(page, size, sortDirection, sort);
            Page<WebsiteResponse> websites = websiteService.getWebsites(name, lang, tags, isActive, pageable);

            PaginatedResponse<WebsiteResponse> response = PaginatedResponse.of(websites.getContent(), websites.getNumber(), websites.getSize(), websites.getTotalElements());
            return ResponseEntity.ok(ApiResponse.success(response, "Websites found"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(500, "Error searching websites: " + e.getMessage(), null));
        }
    }

    /**
     * Get website by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<WebsiteResponse>> getWebsiteById(@PathVariable String id) {
        WebsiteResponse website = websiteService.getWebsiteById(id);
        return ResponseEntity.ok(ApiResponse.success(website, "Website retrieved successfully"));
    }

    /**
     * Get websites by language
     */
    @GetMapping("/by-language/{lang}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<WebsiteResponse>>> getWebsitesByLanguage(
            @PathVariable Website.Language lang,
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        List<WebsiteResponse> websites = websiteService.getWebsitesByLanguage(lang, activeOnly);
        return ResponseEntity.ok(ApiResponse.success(websites, "Websites retrieved successfully"));
    }

    /**
     * Get websites by tag
     */
    @GetMapping("/by-tag")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<WebsiteResponse>>> getWebsitesByTag(
            @RequestParam String tag,
            @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        List<WebsiteResponse> websites = websiteService.getWebsitesByTag(tag, activeOnly);
        return ResponseEntity.ok(ApiResponse.success(websites, "Websites retrieved successfully"));
    }

    /**
     * Get top websites
     */
    @GetMapping("/top")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<WebsiteResponse>>> getTopWebsites(
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<WebsiteResponse> websites = websiteService.getTopWebsites(limit);
        return ResponseEntity.ok(ApiResponse.success(websites, "Top websites retrieved successfully"));
    }

    /**
     * Get website statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<WebsiteService.WebsiteStatistics>> getStatistics() {
        WebsiteService.WebsiteStatistics statistics = websiteService.getStatistics();
        return ResponseEntity.ok(ApiResponse.success(statistics, "Statistics retrieved successfully"));
    }

    /**
     * Create a new website
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_EDITOR')")
    public ResponseEntity<ApiResponse<WebsiteResponse>> createWebsite(
            @Valid @RequestBody WebsiteCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        String createdBy = extractUserIdFromRequest(httpRequest);
        WebsiteResponse website = websiteService.createWebsite(request, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(website, "Website created successfully"));
    }

    /**
     * Update an existing website
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_EDITOR')")
    public ResponseEntity<ApiResponse<WebsiteResponse>> updateWebsite(
            @PathVariable String id,
            @Valid @RequestBody WebsiteUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        String updatedBy = extractUserIdFromRequest(httpRequest);
        WebsiteResponse website = websiteService.updateWebsite(id, request, updatedBy);
        return ResponseEntity.ok(ApiResponse.success(website, "Website updated successfully"));
    }

    /**
     * Delete a website
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteWebsite(@PathVariable String id) {
        websiteService.deleteWebsite(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Website deleted successfully"));
    }

    /**
     * Deactivate a website (soft delete)
     */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_EDITOR')")
    public ResponseEntity<ApiResponse<WebsiteResponse>> deactivateWebsite(
            @PathVariable String id,
            HttpServletRequest httpRequest
    ) {
        String updatedBy = extractUserIdFromRequest(httpRequest);
        WebsiteResponse website = websiteService.deactivateWebsite(id, updatedBy);
        return ResponseEntity.ok(ApiResponse.success(website, "Website deactivated successfully"));
    }

    /**
     * Activate a website
     */
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_EDITOR')")
    public ResponseEntity<ApiResponse<WebsiteResponse>> activateWebsite(
            @PathVariable String id,
            HttpServletRequest httpRequest
    ) {
        String updatedBy = extractUserIdFromRequest(httpRequest);
        WebsiteResponse website = websiteService.activateWebsite(id, updatedBy);
        return ResponseEntity.ok(ApiResponse.success(website, "Website activated successfully"));
    }

    /**
     * Bulk activate websites
     */
    @PatchMapping("/bulk/activate")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<String>> bulkActivateWebsites(
            @RequestBody List<String> ids,
            HttpServletRequest httpRequest
    ) {
        String updatedBy = extractUserIdFromRequest(httpRequest);
        int count = 0;
        for (String id : ids) {
            try {
                websiteService.activateWebsite(id, updatedBy);
                count++;
            } catch (Exception e) {
                // Log error but continue with other IDs
            }
        }
        return ResponseEntity.ok(ApiResponse.success(null, String.format("Successfully activated %d websites", count)));
    }

    /**
     * Bulk deactivate websites
     */
    @PatchMapping("/bulk/deactivate")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<String>> bulkDeactivateWebsites(
            @RequestBody List<String> ids,
            HttpServletRequest httpRequest
    ) {
        String updatedBy = extractUserIdFromRequest(httpRequest);
        int count = 0;
        for (String id : ids) {
            try {
                websiteService.deactivateWebsite(id, updatedBy);
                count++;
            } catch (Exception e) {
                // Log error but continue with other IDs
            }
        }
        return ResponseEntity.ok(ApiResponse.success(null, String.format("Successfully deactivated %d websites", count)));
    }

    /**
     * Extract user ID from JWT token in the Authorization header
     * @param request HttpServletRequest containing the Authorization header
     * @return User ID extracted from token
     */
    private String extractUserIdFromRequest(HttpServletRequest request) {
        return jwtUtils.extractUserIdFromToken(request);
    }
}