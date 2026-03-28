package org.ganjp.api.cms.image;

import lombok.RequiredArgsConstructor;
import org.ganjp.api.auth.security.JwtUtils;

import org.ganjp.api.common.util.CmsUtil;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/images")
@RequiredArgsConstructor
public class ImageController {
    private final ImageService imageService;
    private final JwtUtils jwtUtils;

    /**
     * Search images with pagination and filtering
     * GET /v1/images?name=xxx&lang=EN&tags=yyy&isActive=true&page=0&size=20&sort=updatedAt&direction=desc
     * 
     * @param page Page number (0-based)
     * @param size Page size
     * @param sort Sort field (e.g., updatedAt, createdAt, name)
     * @param direction Sort direction (asc or desc)
     * @param name Optional name filter
     * @param lang Optional language filter
     * @param tags Optional tags filter
     * @param isActive Optional active status filter
     * @param keyword Optional keyword for backward compatibility
     * @return List of images
     */
    @GetMapping()
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<ImageResponse>>> searchImages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Image.Language lang,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String keyword
    ) {
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) 
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        
        Pageable pageable = PageRequest.of(page, size, sortDirection, sort);
        
        // backward compatibility: if keyword is provided use the old simple search
        Page<ImageResponse> images;
        if (keyword != null && !keyword.isBlank()) {
            images = imageService.searchImages(keyword, pageable);
        } else {
            images = imageService.searchImages(name, lang, tags, isActive, pageable);
        }

        PaginatedResponse<ImageResponse> response = PaginatedResponse.of(images.getContent(), images.getNumber(), images.getSize(), images.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success(response, "Images found"));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ImageResponse>>> listImages() {
        List<ImageResponse> images = imageService.listImages();
        return ResponseEntity.ok(ApiResponse.success(images, "Images listed"));
    }


    /**
     * Create a new image from file upload
     * POST /v1/images
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ImageResponse>> createImage(
            @Valid @ModelAttribute ImageCreateRequest request,
            HttpServletRequest httpRequest) throws IOException {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        ImageResponse response = imageService.createImage(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Image created"));
    }

    /**
     * Create a new image from URL (JSON payload)
     * POST /v1/images (application/json)
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ImageResponse>> createImageFromUrl(
            @Valid @RequestBody ImageCreateRequest request,
            HttpServletRequest httpRequest) throws IOException {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        ImageResponse response = imageService.createImage(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Image created"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ImageResponse>> getImageById(@PathVariable String id) {
        ImageResponse response = imageService.getImageById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Image found"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ImageResponse>> updateImage(
            @PathVariable String id,
            @Valid @RequestBody ImageUpdateRequest request,
            HttpServletRequest httpRequest) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        ImageResponse response = imageService.updateImage(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Image updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        imageService.deleteImage(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Image deleted"));
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> permanentlyDeleteImage(@PathVariable String id) {
        imageService.permanentlyDeleteImage(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Image permanently deleted"));
    }

    /**
     * View image file in browser by filename
     * GET /v1/images/view/{filename}
     * Returns the actual image file to be displayed in browser
     */
    @GetMapping("/view/{filename}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Resource> viewImage(@PathVariable String filename) throws IOException {
        File imageFile = imageService.getImageFileByFilename(filename);
        Resource resource = new FileSystemResource(imageFile);

        String contentType = CmsUtil.determineContentType(filename);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }
}
