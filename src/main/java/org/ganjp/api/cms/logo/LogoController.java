package org.ganjp.api.cms.logo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.security.JwtUtils;
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
            HttpServletRequest httpRequest) throws IOException {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        LogoResponse response = logoService.createLogo(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Logo created"));
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
            HttpServletRequest httpRequest) throws IOException {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        LogoResponse response = logoService.createLogo(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Logo created"));
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
            HttpServletRequest httpRequest) throws IOException {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        LogoResponse response = logoService.updateLogo(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Logo updated successfully"));
    }

    /**
     * Get logo by ID
     * GET /v1/logos/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LogoResponse>> getLogoById(@PathVariable String id) {
        LogoResponse response = logoService.getLogoById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Logo retrieved successfully"));
    }

    /**
     * View logo file in browser by filename
     * GET /v1/logos/view/{filename}
     * Returns the actual image file to be displayed in browser
     */
    @GetMapping("/view/{filename}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Resource> viewLogo(@PathVariable String filename) throws IOException {
        File logoFile = logoService.getLogoFileByFilename(filename);
        Resource resource = new FileSystemResource(logoFile);
        
        String contentType = CmsUtil.determineContentType(filename);
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
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
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        logoService.deleteLogo(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Logo deleted successfully"));
    }

    /**
     * Permanently delete logo (hard delete)
     * DELETE /v1/logos/{id}/permanent
     */
    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> permanentlyDeleteLogo(@PathVariable String id) {
        logoService.permanentlyDeleteLogo(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Logo permanently deleted successfully"));
    }
}
