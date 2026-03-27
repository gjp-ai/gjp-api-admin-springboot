package org.ganjp.api.cms.file;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.ganjp.api.cms.file.FileService;
import org.ganjp.api.auth.security.JwtUtils;
import org.ganjp.api.common.model.ApiResponse;
import org.ganjp.api.common.model.PaginatedResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.List;
import org.ganjp.api.cms.file.FileAsset;

@RestController
@RequestMapping("/v1/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {
    private final FileService fileService;
    private final JwtUtils jwtUtils;

    /**
     * List files with pagination and filtering
     * GET /v1/files?name=xxx&lang=EN&tags=yyy&isActive=true&page=0&size=20&sort=updatedAt&direction=desc
     * 
     * @param page Page number (0-based)
     * @param size Page size
     * @param sort Sort field (e.g., updatedAt, createdAt, name)
     * @param direction Sort direction (asc or desc)
     * @param name Optional name filter
     * @param lang Optional language filter
     * @param tags Optional tags filter
     * @param isActive Optional active status filter
     * @return List of files
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<FileResponse>>> listFiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String lang,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) Boolean isActive) {
        try {
            Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) 
                ? Sort.Direction.DESC : Sort.Direction.ASC;
            
            Pageable pageable = PageRequest.of(page, size, sortDirection, sort);
            
            FileAsset.Language langEnum = null;
            if (lang != null && !lang.isBlank()) {
                try {
                    langEnum = FileAsset.Language.valueOf(lang.toUpperCase());
                } catch (IllegalArgumentException ex) {
                    return ResponseEntity.badRequest().body(ApiResponse.error(400, "Invalid lang value", null));
                }
            }

            Page<FileResponse> list;
            // if any search parameter provided, use search; otherwise return all
            if (name != null || langEnum != null || tags != null || isActive != null) {
                list = fileService.searchFiles(name, langEnum, tags, isActive, pageable);
            } else {
                // For consistency, we should probably use searchFiles even without filters, 
                // or ensure listFiles supports pagination. 
                // Given the previous pattern, let's use searchFiles with nulls which acts as "find all" with pagination.
                list = fileService.searchFiles(null, null, null, null, pageable);
            }

            PaginatedResponse<FileResponse> response = PaginatedResponse.of(list.getContent(), list.getNumber(), list.getSize(), list.getTotalElements());
            return ResponseEntity.ok(ApiResponse.success(response, "Files found"));

        } catch (Exception e) {
            log.error("Error listing files", e);
            return ResponseEntity.status(500).body(ApiResponse.error(500, "Error listing files: " + e.getMessage(), null));
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<FileResponse>> createFile(@Valid @ModelAttribute FileCreateRequest request, HttpServletRequest httpRequest) {
        try {
            String userId = jwtUtils.extractUserIdFromToken(httpRequest);
            FileResponse r = fileService.createFile(request, userId);
            return ResponseEntity.status(201).body(ApiResponse.success(r, "File created"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error creating file", e);
            return ResponseEntity.status(500).body(ApiResponse.error(500, "Error creating file: " + e.getMessage(), null));
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<FileResponse>> createFileJson(@Valid @RequestBody FileCreateRequest request, HttpServletRequest httpRequest) {
        try {
            String userId = jwtUtils.extractUserIdFromToken(httpRequest);
            FileResponse r = fileService.createFile(request, userId);
            return ResponseEntity.status(201).body(ApiResponse.success(r, "File created"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error creating file (json)", e);
            return ResponseEntity.status(500).body(ApiResponse.error(500, "Error creating file: " + e.getMessage(), null));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<FileResponse>> getFile(@PathVariable String id) {
        try {
            FileResponse r = fileService.getFileById(id);
            if (r == null) return ResponseEntity.status(404).body(ApiResponse.error(404, "File not found", null));
            return ResponseEntity.ok(ApiResponse.success(r, "File found"));
        } catch (Exception e) {
            log.error("Error fetching file", e);
            return ResponseEntity.status(500).body(ApiResponse.error(500, "Error fetching file: " + e.getMessage(), null));
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<FileResponse>> updateFile(@PathVariable String id, @Valid @ModelAttribute FileUpdateRequest request, HttpServletRequest httpRequest) {
        try {
            String userId = jwtUtils.extractUserIdFromToken(httpRequest);
            FileResponse r = fileService.updateFile(id, request, userId);
            if (r == null) return ResponseEntity.status(404).body(ApiResponse.error(404, "File not found", null));
            return ResponseEntity.ok(ApiResponse.success(r, "File updated"));
        } catch (Exception e) {
            log.error("Error updating file", e);
            return ResponseEntity.status(500).body(ApiResponse.error(500, "Error updating file: " + e.getMessage(), null));
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<FileResponse>> updateFileJson(@PathVariable String id, @Valid @RequestBody FileUpdateRequest request, HttpServletRequest httpRequest) {
        try {
            String userId = jwtUtils.extractUserIdFromToken(httpRequest);
            FileResponse r = fileService.updateFile(id, request, userId);
            if (r == null) return ResponseEntity.status(404).body(ApiResponse.error(404, "File not found", null));
            return ResponseEntity.ok(ApiResponse.success(r, "File updated"));
        } catch (Exception e) {
            log.error("Error updating file (json)", e);
            return ResponseEntity.status(500).body(ApiResponse.error(500, "Error updating file: " + e.getMessage(), null));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable String id, HttpServletRequest httpRequest) {
        try {
            String userId = jwtUtils.extractUserIdFromToken(httpRequest);
            boolean ok = fileService.deleteFile(id, userId);
            if (!ok) return ResponseEntity.status(404).body(ApiResponse.error(404, "File not found", null));
            return ResponseEntity.ok(ApiResponse.success(null, "File deleted"));
        } catch (Exception e) {
            log.error("Error deleting file", e);
            return ResponseEntity.status(500).body(ApiResponse.error(500, "Error deleting file: " + e.getMessage(), null));
        }
    }

    // download file by filename (secured)
    @GetMapping("/download/{filename}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> downloadByFilename(@PathVariable String filename) {
        try {
            java.io.File file = fileService.getFileByFilename(filename);
            java.io.InputStream is = new java.io.FileInputStream(file);
            InputStreamResource resource = new InputStreamResource(is);
            String ct = org.ganjp.api.common.util.CmsUtil.determineContentType(filename);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(ct))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentLength(file.length())
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IOException e) {
            log.error("Error reading file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
