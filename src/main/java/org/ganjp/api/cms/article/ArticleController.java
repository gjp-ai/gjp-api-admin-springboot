package org.ganjp.api.cms.article;

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
@RequestMapping("/v1/articles")
@RequiredArgsConstructor
public class ArticleController {
    private final ArticleService articleService;
    private final JwtUtils jwtUtils;

    /**
     * Search articles with pagination and filtering
     * GET /v1/articles?title=xxx&lang=EN&tags=yyy&isActive=true&page=0&size=20&sort=updatedAt&direction=desc
     * 
     * @param page Page number (0-based)
     * @param size Page size
     * @param sort Sort field (e.g., updatedAt, createdAt, title)
     * @param direction Sort direction (asc or desc)
     * @param title Optional title filter
     * @param lang Optional language filter
     * @param tags Optional tags filter
     * @param isActive Optional active status filter
     * @return List of articles
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<ArticleResponse>>> searchArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Article.Language lang,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) Boolean isActive
    ) {
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) 
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        
        Pageable pageable = PageRequest.of(page, size, sortDirection, sort);
        Page<ArticleResponse> list = articleService.searchArticles(title, lang, tags, isActive, pageable);
        PaginatedResponse<ArticleResponse> response = PaginatedResponse.of(list.getContent(), list.getNumber(), list.getSize(), list.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success(response, "Articles found"));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ArticleResponse>>> listArticles() {
        List<ArticleResponse> list = articleService.listArticles();
        return ResponseEntity.ok(ApiResponse.success(list, "Articles listed"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ArticleResponse>> createArticle(@Valid @ModelAttribute ArticleCreateRequest request, HttpServletRequest httpRequest) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        ArticleResponse res = articleService.createArticle(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(res, "Article created"));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ArticleResponse>> createArticleJson(@Valid @RequestBody ArticleCreateRequest request, HttpServletRequest httpRequest) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        ArticleResponse res = articleService.createArticle(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(res, "Article created"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ArticleResponse>> getArticle(@PathVariable String id) {
        ArticleResponse r = articleService.getArticleById(id);
        return ResponseEntity.ok(ApiResponse.success(r, "Article found"));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ArticleResponse>> updateArticle(@PathVariable String id, @Valid @ModelAttribute ArticleUpdateRequest request, HttpServletRequest httpRequest) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        ArticleResponse r = articleService.updateArticle(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(r, "Article updated"));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ArticleResponse>> updateArticleJson(@PathVariable String id, @Valid @RequestBody ArticleUpdateRequest request, HttpServletRequest httpRequest) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        ArticleResponse r = articleService.updateArticle(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(r, "Article updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteArticle(@PathVariable String id, HttpServletRequest httpRequest) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        articleService.deleteArticle(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Article deleted"));
    }

    // serve cover image (supports Range)
    @GetMapping("/cover/{filename}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> viewCover(@PathVariable String filename, @RequestHeader(value = "Range", required = false) String rangeHeader) throws IOException {
        java.io.File file = articleService.getCoverImageFileByFilename(filename);
        long contentLength = file.length();
        String contentType = org.ganjp.api.common.util.CmsUtil.determineContentType(filename);

        if (rangeHeader == null) {
            InputStreamResource full = new InputStreamResource(new java.io.FileInputStream(file));
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .contentLength(contentLength)
                    .body(full);
        }

        HttpRange httpRange = HttpRange.parseRanges(rangeHeader).get(0);
        long start = httpRange.getRangeStart(contentLength);
        long end = httpRange.getRangeEnd(contentLength);
        long rangeLength = end - start + 1;

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
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + contentLength)
                .contentLength(rangeLength)
                .body(resource);
    }
}
