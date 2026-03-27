package org.ganjp.api.cms.logo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.security.JwtUtils;
import org.ganjp.api.cms.file.FileResponse;
import org.ganjp.api.cms.logo.LogoCreateRequest;
import org.ganjp.api.cms.logo.LogoResponse;
import org.ganjp.api.cms.logo.LogoUpdateRequest;
import org.ganjp.api.cms.logo.LogoService;
import org.ganjp.api.common.model.ApiResponse;
import org.ganjp.api.common.model.PaginatedResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.ganjp.api.common.util.CmsUtil;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * REST Controller for Logo CRUD operations
 */
@RestController
@RequestMapping("/v1/logos")
@RequiredArgsConstructor
@Slf4j
public class LogoController {

    private final LogoService logoService;
    private final JwtUtils jwtUtils;
    
    // Constants for error messages
    private static final String LOGO_NOT_FOUND_MSG = "Logo not found";
    private static final String LOGO_NOT_FOUND_ERROR = "Logo not found: ";

    /**
     * Flexible search logos by name, language, tags, and status with pagination
     * GET /v1/logos?name=xxx&lang=EN&tags=xxx&isActive=true&page=0&size=20&sort=updatedAt&direction=desc
     * 
     * @param page Page number (0-based)
     * @param size Page size
     * @param sort Sort field (e.g., updatedAt, createdAt, name)
     * @param direction Sort direction (asc or desc)
     * @param name Optional name filter
     * @param lang Optional language filter
     * @param tags Optional tags filter
     * @param isActive Optional active status filter
     * @return List of logos
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<LogoResponse>>> searchLogos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Logo.Language lang,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) Boolean isActive) {
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) 
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        
        Pageable pageable = PageRequest.of(page, size, sortDirection, sort);
        Page<LogoResponse> logos = logoService.searchLogos(name, lang, tags, isActive, pageable);

        PaginatedResponse<LogoResponse> response = PaginatedResponse.of(logos.getContent(), logos.getNumber(), logos.getSize(), logos.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success(response, "Logos found"));
    }

    /**
     * Get all logos (including inactive)
     * GET /v1/logos/all
     */
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<LogoResponse>>> getAllLogos() {
        List<LogoResponse> logos = logoService.getAllLogos();
        return ResponseEntity.ok(ApiResponse.success(logos, "All logos retrieved successfully"));
    }

    /**
     * Create a new logo
     * POST /v1/logos
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LogoResponse>> createLogo(
            @Valid @ModelAttribute LogoCreateRequest request,
            HttpServletRequest httpRequest) {
        try {
            String userId = extractUserIdFromRequest(httpRequest);
            LogoResponse response = logoService.createLogo(request, userId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response, "Logo created successfully"));
        } catch (IOException e) {
            log.error("Error creating logo", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Error creating logo: " + e.getMessage(), null));
        } catch (IllegalArgumentException e) {
            log.error("Invalid request", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Invalid request: " + e.getMessage(), null));
        }
    }

    /**
     * Create a new logo from URL (JSON payload)
     * POST /v1/logos/from-url
     * Request body: { "originalUrl": "https://...", "name": "Logo Name", "tags": "tag1,tag2" }
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LogoResponse>> createLogoFromUrl(
            @Valid @RequestBody LogoCreateRequest request,
            HttpServletRequest httpRequest) {
        try {
            // Validate that originalUrl is provided
            if (request.getOriginalUrl() == null || request.getOriginalUrl().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "originalUrl is required", null));
            }
            
            String userId = extractUserIdFromRequest(httpRequest);
            LogoResponse response = logoService.createLogo(request, userId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response, "Logo created successfully from URL"));
        } catch (IOException e) {
            log.error("Error creating logo from URL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Error creating logo from URL: " + e.getMessage(), null));
        } catch (IllegalArgumentException e) {
            log.error("Invalid URL or request", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Invalid request: " + e.getMessage(), null));
        }
    }

    /**
     * Update an existing logo
     * PUT /v1/logos/{id}
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LogoResponse>> updateLogo(
            @PathVariable String id,
            @Valid @RequestBody LogoUpdateRequest request,
            HttpServletRequest httpRequest) {
        try {
            String userId = extractUserIdFromRequest(httpRequest);
            LogoResponse response = logoService.updateLogo(id, request, userId);
            return ResponseEntity.ok(ApiResponse.success(response, "Logo updated successfully"));
        } catch (IOException e) {
            log.error("Error updating logo", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Error updating logo: " + e.getMessage(), null));
        } catch (IllegalArgumentException e) {
            log.error("Logo not found or invalid request", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, LOGO_NOT_FOUND_ERROR + e.getMessage(), null));
        }
    }

    /**
     * Get logo by ID
     * GET /v1/logos/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LogoResponse>> getLogoById(@PathVariable String id) {
        try {
            LogoResponse response = logoService.getLogoById(id);
            return ResponseEntity.ok(ApiResponse.success(response, "Logo retrieved successfully"));
        } catch (IllegalArgumentException e) {
            log.error(LOGO_NOT_FOUND_MSG, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, LOGO_NOT_FOUND_ERROR + e.getMessage(), null));
        }
    }

    /**
     * View logo file in browser by filename
     * GET /v1/logos/view/{filename}
     * Returns the actual image file to be displayed in browser
     */
    @GetMapping("/view/{filename}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Resource> viewLogo(@PathVariable String filename) {
        try {
            File logoFile = logoService.getLogoFileByFilename(filename);
            Resource resource = new FileSystemResource(logoFile);
            
            // Determine content type based on file extension
            String contentType = CmsUtil.determineContentType(filename);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (IllegalArgumentException e) {
            log.error("Logo not found: {}", filename, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IOException e) {
            log.error("Error reading logo file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Find logos by tag
     * GET /v1/logos/tag?tag=xxx
     */
    @GetMapping("/tag")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<LogoResponse>>> findLogosByTag(@RequestParam String tag) {
        List<LogoResponse> logos = logoService.findLogosByTag(tag);
        return ResponseEntity.ok(ApiResponse.success(logos, "Logos retrieved successfully"));
    }

    /**
     * Soft delete logo (set isActive to false)
     * DELETE /v1/logos/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteLogo(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        try {
            String userId = extractUserIdFromRequest(httpRequest);
            logoService.deleteLogo(id, userId);
            return ResponseEntity.ok(ApiResponse.success(null, "Logo deleted successfully"));
        } catch (IllegalArgumentException e) {
            log.error(LOGO_NOT_FOUND_MSG, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, LOGO_NOT_FOUND_ERROR + e.getMessage(), null));
        }
    }

    /**
     * Permanently delete logo (hard delete)
     * DELETE /v1/logos/{id}/permanent
     */
    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> permanentlyDeleteLogo(@PathVariable String id) {
        try {
            logoService.permanentlyDeleteLogo(id);
            return ResponseEntity.ok(ApiResponse.success(null, "Logo permanently deleted successfully"));
        } catch (IllegalArgumentException e) {
            log.error(LOGO_NOT_FOUND_MSG, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, LOGO_NOT_FOUND_ERROR + e.getMessage(), null));
        }
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
