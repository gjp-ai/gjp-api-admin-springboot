package org.ganjp.api.cms.article.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.security.JwtUtils;
import org.ganjp.api.cms.article.image.ArticleImageCreateRequest;
import org.ganjp.api.cms.article.image.ArticleImageResponse;
import org.ganjp.api.cms.article.image.ArticleImageUpdateRequest;
import org.ganjp.api.cms.article.image.ArticleImage;
import org.ganjp.api.cms.article.image.ArticleImageService;
import org.ganjp.api.common.model.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/article-images")
@RequiredArgsConstructor
public class ArticleImageController {
    private final ArticleImageService articleImageService;
    private final JwtUtils jwtUtils;

    @GetMapping("/view/{filename:.+}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Resource> viewImage(@PathVariable String filename) {
        Resource file = articleImageService.getImage(filename);
        String contentType = org.ganjp.api.common.util.CmsUtil.determineContentType(filename);
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFilename() + "\"")
                .body(file);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ArticleImageResponse>>> searchArticleImages(
            @RequestParam(required = false) String articleId,
            @RequestParam(required = false) ArticleImage.Language lang,
            @RequestParam(required = false) Boolean isActive
    ) {
        List<ArticleImageResponse> images = articleImageService.searchArticleImages(articleId, lang, isActive);
        return ResponseEntity.ok(ApiResponse.success(images, "Article images found"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ArticleImageResponse>> getArticleImage(@PathVariable String id) {
        ArticleImageResponse image = articleImageService.getArticleImageById(id);
        if (image == null) {
            return ResponseEntity.status(404).body(ApiResponse.error(404, "Article image not found", null));
        }
        return ResponseEntity.ok(ApiResponse.success(image, "Article image found"));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ArticleImageResponse>> createArticleImageJson(
            @Valid @RequestBody ArticleImageCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        if (request.getOriginalUrl() == null || request.getOriginalUrl().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Original URL is required", null));
        }
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        ArticleImageResponse image = articleImageService.createArticleImage(request, userId);
        return ResponseEntity.ok(ApiResponse.success(image, "Article image created"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ArticleImageResponse>> createArticleImage(
            @Valid @ModelAttribute ArticleImageCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        ArticleImageResponse image = articleImageService.createArticleImage(request, userId);
        return ResponseEntity.ok(ApiResponse.success(image, "Article image created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ArticleImageResponse>> updateArticleImage(
            @PathVariable String id,
            @Valid @RequestBody ArticleImageUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        ArticleImageResponse image = articleImageService.updateArticleImage(id, request, userId);
        if (image == null) {
            return ResponseEntity.status(404).body(ApiResponse.error(404, "Article image not found", null));
        }
        return ResponseEntity.ok(ApiResponse.success(image, "Article image updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteArticleImage(@PathVariable String id) {
        articleImageService.deleteArticleImage(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Article image deleted"));
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<ApiResponse<Void>> deleteArticleImagePermanently(@PathVariable String id) {
        articleImageService.deleteArticleImagePermanently(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Article image permanently deleted"));
    }
}
