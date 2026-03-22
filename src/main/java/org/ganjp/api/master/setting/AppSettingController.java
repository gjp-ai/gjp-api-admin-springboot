package org.ganjp.api.master.setting;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.ganjp.api.auth.user.UserResponse;
import org.ganjp.api.auth.security.JwtUtils;
import org.ganjp.api.master.setting.AppSettingResponse;
import org.ganjp.api.master.setting.AppSettingCreateRequest;
import org.ganjp.api.master.setting.AppSettingUpdateRequest;
import org.ganjp.api.master.setting.AppSetting;
import org.ganjp.api.master.setting.AppSettingService;
import org.ganjp.api.common.model.ApiResponse;
import org.ganjp.api.common.model.PaginatedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for App Settings management
 */
@RestController
@RequestMapping("/v1/app-settings")
@RequiredArgsConstructor
public class AppSettingController {

    private final AppSettingService appSettingService;
    private final JwtUtils jwtUtils;

    /**
     * Get all app settings with pagination and filtering
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<AppSettingResponse>>> getSettings(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) AppSetting.Language lang,
            @RequestParam(required = false) Boolean isPublic,
            @RequestParam(required = false) Boolean isSystem,
            Pageable pageable) {

        Page<AppSettingResponse> settings = appSettingService.getSettings(searchTerm, lang, isPublic, isSystem, pageable);


        PaginatedResponse<AppSettingResponse> response = PaginatedResponse.of(settings.getContent(), settings.getNumber(), settings.getSize(), settings.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success(response, "App settings found"));
    }

    /**
     * Get app setting by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<AppSettingResponse>> getSettingById(@PathVariable String id) {
        AppSettingResponse setting = appSettingService.getSettingById(id);
        return ResponseEntity.ok(ApiResponse.success(setting, "App setting retrieved successfully"));
    }

    /**
     * Get app setting by name and language
     */
    @GetMapping("/by-name")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<AppSettingResponse>> getSettingByNameAndLang(
            @RequestParam String name,
            @RequestParam AppSetting.Language lang) {

        AppSettingResponse setting = appSettingService.getSettingByNameAndLang(name, lang);
        return ResponseEntity.ok(ApiResponse.success(setting, "App setting retrieved successfully"));
    }

    /**
     * Get all settings for a specific name (all languages)
     */
    @GetMapping("/by-name/{name}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<AppSettingResponse>>> getSettingsByName(@PathVariable String name) {
        List<AppSettingResponse> settings = appSettingService.getSettingsByName(name);
        return ResponseEntity.ok(ApiResponse.success(settings, "App settings retrieved successfully"));
    }

    /**
     * Get settings by language
     */
    @GetMapping("/by-language/{lang}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<AppSettingResponse>>> getSettingsByLang(@PathVariable AppSetting.Language lang) {
        List<AppSettingResponse> settings = appSettingService.getSettingsByLang(lang);
        return ResponseEntity.ok(ApiResponse.success(settings, "App settings retrieved successfully"));
    }

    /**
     * Get public settings (accessible by all authenticated users)
     */
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<List<AppSettingResponse>>> getPublicSettings() {
        List<AppSettingResponse> settings = appSettingService.getPublicSettings();
        return ResponseEntity.ok(ApiResponse.success(settings, "Public app settings retrieved successfully"));
    }

    /**
     * Get public settings by language (accessible by all authenticated users)
     */
    @GetMapping("/public/{lang}")
    public ResponseEntity<ApiResponse<List<AppSettingResponse>>> getPublicSettingsByLang(@PathVariable AppSetting.Language lang) {
        List<AppSettingResponse> settings = appSettingService.getPublicSettingsByLang(lang);
        return ResponseEntity.ok(ApiResponse.success(settings, "Public app settings retrieved successfully"));
    }

    /**
     * Get user-editable settings
     */
    @GetMapping("/user-editable")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<AppSettingResponse>>> getUserEditableSettings() {
        List<AppSettingResponse> settings = appSettingService.getUserEditableSettings();
        return ResponseEntity.ok(ApiResponse.success(settings, "User-editable app settings retrieved successfully"));
    }

    /**
     * Get user-editable settings by language
     */
    @GetMapping("/user-editable/{lang}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<AppSettingResponse>>> getUserEditableSettingsByLang(@PathVariable AppSetting.Language lang) {
        List<AppSettingResponse> settings = appSettingService.getUserEditableSettingsByLang(lang);
        return ResponseEntity.ok(ApiResponse.success(settings, "User-editable app settings retrieved successfully"));
    }

    /**
     * Get setting value by name and language
     */
    @GetMapping("/value")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<String>> getSettingValue(
            @RequestParam String name,
            @RequestParam AppSetting.Language lang,
            @RequestParam(required = false) String defaultValue) {

        String value = appSettingService.getSettingValue(name, lang, defaultValue);
        return ResponseEntity.ok(ApiResponse.success(value, "App setting value retrieved successfully"));
    }

    /**
     * Get distinct setting names
     */
    @GetMapping("/names")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<String>>> getDistinctNames() {
        List<String> names = appSettingService.getDistinctNames();
        return ResponseEntity.ok(ApiResponse.success(names, "Setting names retrieved successfully"));
    }

    /**
     * Get setting statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSettingStatistics() {
        Map<String, Object> statistics = appSettingService.getSettingStatistics();
        return ResponseEntity.ok(ApiResponse.success(statistics, "Setting statistics retrieved successfully"));
    }

    /**
     * Create new app setting
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<AppSettingResponse>> createSetting(
            @Valid @RequestBody AppSettingCreateRequest request,
            HttpServletRequest httpRequest) {

        String createdBy = extractUserIdFromRequest(httpRequest);
        AppSettingResponse setting = appSettingService.createSetting(request, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(setting, "App setting created successfully"));
    }

    /**
     * Update app setting
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<AppSettingResponse>> updateSetting(
            @PathVariable String id,
            @Valid @RequestBody AppSettingUpdateRequest request,
            HttpServletRequest httpRequest) {

        String updatedBy = extractUserIdFromRequest(httpRequest);
        AppSettingResponse setting = appSettingService.updateSetting(id, request, updatedBy);
        return ResponseEntity.ok(ApiResponse.success(setting, "App setting updated successfully"));
    }

    /**
     * Update setting value by name and language
     */
    @PutMapping("/value")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<AppSettingResponse>> updateSettingValue(
            @RequestParam String name,
            @RequestParam AppSetting.Language lang,
            @RequestParam String value,
            HttpServletRequest httpRequest) {

        String updatedBy = extractUserIdFromRequest(httpRequest);
        AppSettingResponse setting = appSettingService.updateSettingValue(name, lang, value, updatedBy);
        return ResponseEntity.ok(ApiResponse.success(setting, "App setting value updated successfully"));
    }

    /**
     * Delete app setting
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteSetting(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        String deletedBy = extractUserIdFromRequest(httpRequest);
        appSettingService.deleteSetting(id, deletedBy);
        return ResponseEntity.ok(ApiResponse.success("Setting deleted", "App setting deleted successfully"));
    }

    /**
     * Delete setting by name and language
     */
    @DeleteMapping("/by-name")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteSettingByNameAndLang(
            @RequestParam String name,
            @RequestParam AppSetting.Language lang,
            HttpServletRequest httpRequest) {

        String deletedBy = extractUserIdFromRequest(httpRequest);
        appSettingService.deleteSettingByNameAndLang(name, lang, deletedBy);
        return ResponseEntity.ok(ApiResponse.success("Setting deleted", "App setting deleted successfully"));
    }

    /**
     * Check if setting exists
     */
    @GetMapping("/exists")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> settingExists(
            @RequestParam String name,
            @RequestParam AppSetting.Language lang) {

        boolean exists = appSettingService.settingExists(name, lang);
        return ResponseEntity.ok(ApiResponse.success(exists, "Setting existence check completed"));
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
