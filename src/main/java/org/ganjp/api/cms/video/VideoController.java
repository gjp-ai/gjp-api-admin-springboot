package org.ganjp.api.cms.video;

import lombok.RequiredArgsConstructor;
import org.ganjp.api.auth.security.JwtUtils;

import org.ganjp.api.common.model.ApiResponse;
import org.ganjp.api.common.model.PaginatedResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/videos")
@RequiredArgsConstructor
public class VideoController {
    private final VideoService videoService;
    private final JwtUtils jwtUtils;

    /**
     * Search videos with pagination and filtering
     * GET /v1/videos?name=xxx&lang=EN&tags=yyy&isActive=true&page=0&size=20&sort=updatedAt&direction=desc
     * 
     * @param page Page number (0-based)
     * @param size Page size
     * @param sort Sort field (e.g., updatedAt, createdAt, name)
     * @param direction Sort direction (asc or desc)
     * @param name Optional name filter
     * @param lang Optional language filter
     * @param tags Optional tags filter
     * @param isActive Optional active status filter
     * @return List of videos
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<VideoResponse>>> searchVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Video.Language lang,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) Boolean isActive
    ) {
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) 
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        
        if (size > 100) size = 100;
        Pageable pageable = PageRequest.of(page, size, sortDirection, sort);
        Page<VideoResponse> list = videoService.searchVideos(name, lang, tags, isActive, pageable);
        
        PaginatedResponse<VideoResponse> response = PaginatedResponse.of(list.getContent(), list.getNumber(), list.getSize(), list.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success(response, "Videos found"));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<VideoResponse>>> listVideos() {
        List<VideoResponse> list = videoService.listVideos();
        return ResponseEntity.ok(ApiResponse.success(list, "Videos found"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<VideoResponse>> uploadVideo(@Valid @ModelAttribute VideoCreateRequest request, HttpServletRequest httpRequest) throws IOException {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        VideoResponse res = videoService.createVideo(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(res, "Video created"));
    }

    /**
     * Create a video from a URL (including YouTube).
     * POST /v1/videos (JSON body)
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<VideoResponse>> createVideoByUrl(@Valid @RequestBody VideoCreateByUrlRequest request, HttpServletRequest httpRequest) throws IOException {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        VideoResponse res = videoService.createVideoByUrl(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(res, "Video created from URL"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<VideoResponse>> getVideo(@PathVariable String id) {
        VideoResponse r = videoService.getVideoById(id);
        return ResponseEntity.ok(ApiResponse.success(r, "Video found"));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<VideoResponse>> updateVideo(@PathVariable String id, @Valid @ModelAttribute VideoUpdateRequest request, HttpServletRequest httpRequest) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        VideoResponse r = videoService.updateVideo(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(r, "Video updated"));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<VideoResponse>> updateVideoJson(@PathVariable String id, @Valid @RequestBody VideoUpdateRequest request, HttpServletRequest httpRequest) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        VideoResponse r = videoService.updateVideo(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(r, "Video updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteVideo(@PathVariable String id, HttpServletRequest httpRequest) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        videoService.deleteVideo(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Video deleted"));
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> permanentlyDeleteVideo(@PathVariable String id) {
        videoService.permanentlyDeleteVideo(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Video permanently deleted"));
    }

    @GetMapping("/cover/{filename}")
    public ResponseEntity<?> viewCoverImage(@PathVariable String filename) throws IOException {
        java.io.File file = videoService.getCoverImageFileByFilename(filename);
        String contentType = org.ganjp.api.common.util.CmsUtil.determineContentType(filename);
        InputStreamResource resource = new InputStreamResource(new java.io.FileInputStream(file));
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + org.ganjp.api.common.util.CmsUtil.sanitizeFilename(filename) + "\"")
                .contentLength(file.length())
                .body(resource);
    }

    @GetMapping("/view/{filename}")
    public ResponseEntity<?> viewVideo(@PathVariable String filename, @RequestHeader(value = "Range", required = false) String rangeHeader) throws IOException {
        java.io.File file = videoService.getVideoFileByFilename(filename);
        long contentLength = file.length();
        String contentType = org.ganjp.api.common.util.CmsUtil.determineContentType(filename);

        if (rangeHeader == null) {
            InputStreamResource full = new InputStreamResource(new java.io.FileInputStream(file));
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + org.ganjp.api.common.util.CmsUtil.sanitizeFilename(filename) + "\"")
                    .contentLength(contentLength)
                    .body(full);
        }

        HttpRange httpRange = HttpRange.parseRanges(rangeHeader).get(0);
        long start = httpRange.getRangeStart(contentLength);
        long end = httpRange.getRangeEnd(contentLength);
        long rangeLength = end - start + 1;

        // Stream the requested range without loading entire range into memory
        java.io.InputStream rangeStream = new java.io.InputStream() {
            private final java.io.RandomAccessFile raf;
            private long remaining = rangeLength;
            {
                this.raf = new java.io.RandomAccessFile(file, "r");
                this.raf.seek(start);
            }
            @Override
            public int read() throws java.io.IOException {
                if (remaining <= 0) return -1;
                int b = raf.read();
                if (b != -1) remaining--;
                return b;
            }
            @Override
            public int read(byte[] b, int off, int len) throws java.io.IOException {
                if (remaining <= 0) return -1;
                int toRead = (int) Math.min(len, remaining);
                int r = raf.read(b, off, toRead);
                if (r > 0) remaining -= r;
                return r;
            }
            @Override
            public void close() throws java.io.IOException {
                try { raf.close(); } finally { super.close(); }
            }
        };

        InputStreamResource resource = new InputStreamResource(rangeStream);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + contentLength)
                .contentLength(rangeLength)
                .body(resource);
    }
}
