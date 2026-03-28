package org.ganjp.api.cms.article;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.ganjp.api.common.exception.ResourceNotFoundException;
import org.ganjp.api.common.util.CmsUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ArticleService {
    private final ArticleRepository articleRepository;
    private final ArticleProperties articleProperties;

    public ArticleResponse createArticle(ArticleCreateRequest request, String userId) {
        Article a = new Article();
        String id = UUID.randomUUID().toString();
        a.setId(id);
        a.setTitle(request.getTitle());
        a.setSummary(request.getSummary());
        a.setContent(request.getContent());
        a.setOriginalUrl(request.getOriginalUrl());
        a.setSourceName(request.getSourceName());
        a.setTags(request.getTags());
        if (request.getLang() != null) a.setLang(request.getLang());
        if (request.getDisplayOrder() != null) a.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsActive() != null) a.setIsActive(request.getIsActive());
        if (request.getCoverImageOriginalUrl() != null) a.setCoverImageOriginalUrl(request.getCoverImageOriginalUrl());
        // cover image
        try {
            // determine article upload directory (from article.cover-image.upload.directory)
            String articleCoverImageDir = articleProperties.getCoverImage().getUpload().getDirectory();

            if (request.getCoverImageFile() != null && !request.getCoverImageFile().isEmpty()) {
                MultipartFile cover = request.getCoverImageFile();
                String coverOriginal = cover.getOriginalFilename();
                String coverFilename;
                if (coverOriginal == null || coverOriginal.isBlank()) {
                    coverFilename = System.currentTimeMillis() + "-cover";
                } else {
                    coverFilename = coverOriginal.replaceAll("\\s+", "-");
                }
                Files.createDirectories(Path.of(articleCoverImageDir));
                Path coverTarget = CmsUtil.resolveSecurePath(articleCoverImageDir, coverFilename);

                if (Files.exists(coverTarget)) {
                    throw new IllegalArgumentException("Cover image already exists: " + coverFilename);
                }

                try {
                    BufferedImage original = ImageIO.read(cover.getInputStream());
                    if (original != null) {
                        BufferedImage resized = resizeImageIfNeeded(original, articleProperties.getCoverImage().getUpload().getResize().getMaxSize());
                        String ext = "png";
                        int dot = coverFilename.lastIndexOf('.');
                        if (dot > 0 && dot < coverFilename.length() - 1) ext = coverFilename.substring(dot + 1).toLowerCase();
                        ImageIO.write(resized, ext, coverTarget.toFile());
                    } else {
                        Files.copy(cover.getInputStream(), coverTarget);
                    }
                } catch (IOException e) {
                    Files.copy(cover.getInputStream(), coverTarget);
                }
                a.setCoverImageFilename(coverFilename);
            } else if (request.getCoverImageOriginalUrl() != null && !request.getCoverImageOriginalUrl().isBlank()) {
                // download remote image and save it
                String url = request.getCoverImageOriginalUrl();
                String coverFilename = request.getCoverImageFilename();
                if (coverFilename == null || coverFilename.isBlank()) {
                    // derive filename from URL
                    try {
                        java.net.URL u = new java.net.URL(url);
                        String p = u.getPath();
                        int last = p.lastIndexOf('/');
                        String lastSeg = last >= 0 ? p.substring(last + 1) : p;
                        if (lastSeg == null || lastSeg.isBlank()) lastSeg = System.currentTimeMillis() + "-cover";
                        coverFilename = lastSeg.replaceAll("\\s+", "-");
                    } catch (Exception ex) {
                        coverFilename = System.currentTimeMillis() + "-cover";
                    }
                }

                Files.createDirectories(Path.of(articleCoverImageDir));
                Path coverTarget = CmsUtil.resolveSecurePath(articleCoverImageDir, coverFilename);

                // ensure unique filename
                int suffix = 1;
                String base = coverFilename;
                String ext = "";
                int dot = coverFilename.lastIndexOf('.');
                if (dot > 0) {
                    base = coverFilename.substring(0, dot);
                    ext = coverFilename.substring(dot);
                }
                while (Files.exists(coverTarget)) {
                    coverFilename = base + "-" + suffix + ext;
                    coverTarget = CmsUtil.resolveSecurePath(articleCoverImageDir, coverFilename);
                    suffix++;
                }

                try (java.io.InputStream is = new java.net.URL(url).openStream()) {
                    // try to read as image
                    try {
                        byte[] data = is.readAllBytes();
                        java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(data);
                        BufferedImage original = ImageIO.read(bis);
                        if (original != null) {
                            BufferedImage resized = resizeImageIfNeeded(original, articleProperties.getCoverImage().getUpload().getResize().getMaxSize());
                            String writeExt = "png";
                            if (dot > 0 && dot < coverFilename.length() - 1) writeExt = coverFilename.substring(dot + 1).toLowerCase();
                            ImageIO.write(resized, writeExt, coverTarget.toFile());
                        } else {
                            // fallback - write raw bytes
                            Files.write(coverTarget, data);
                        }
                    } catch (IOException ex) {
                        // fallback - stream copy
                        try (java.io.InputStream is2 = new java.net.URL(url).openStream()) {
                            Files.copy(is2, coverTarget);
                        }
                    }
                }

                a.setCoverImageFilename(coverFilename);
                a.setCoverImageOriginalUrl(request.getCoverImageOriginalUrl());
            } else if (request.getCoverImageFilename() != null) {
                a.setCoverImageFilename(request.getCoverImageFilename());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to save cover image: " + e.getMessage());
        }

        a.setCreatedBy(userId);
        a.setUpdatedBy(userId);

        Article saved = articleRepository.save(a);
        return toResponse(saved);
    }

    public ArticleResponse updateArticle(String id, ArticleUpdateRequest request, String userId) {
        Article a = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", "id", id));

        if ("null".equals(request.getOriginalUrl())) {
            a.setOriginalUrl(null);
            request.setOriginalUrl(null);
        }
        if ("null".equals(request.getSourceName())) {
            a.setSourceName(null);
            request.setSourceName(null);
        }
        if ("null".equals(request.getCoverImageOriginalUrl())) {
            a.setCoverImageOriginalUrl(null);
            request.setCoverImageOriginalUrl(null);
        }
        if ("null".equals(request.getCoverImageFilename())) {
            a.setCoverImageFilename(null);
            request.setCoverImageFilename(null);
        }

        if (request.getTitle() != null) a.setTitle(request.getTitle());
        if (request.getSummary() != null) a.setSummary(request.getSummary());
        if (request.getContent() != null) a.setContent(request.getContent());
        if (request.getOriginalUrl() != null) a.setOriginalUrl(request.getOriginalUrl());
        if (request.getSourceName() != null) a.setSourceName(request.getSourceName());
        // capture existing cover imgage original URL to decide whether we need to re-download
        String existingCoverOriginalUrl = a.getCoverImageOriginalUrl();
        // do NOT set coverImageOriginalUrl here unconditionally — handle it only when the URL changes (to avoid re-downloading)

        try {
            String articleCoverImageDir = articleProperties.getCoverImage().getUpload().getDirectory();
            if (request.getCoverImageFile() != null && !request.getCoverImageFile().isEmpty()) {
                MultipartFile coverFile = request.getCoverImageFile();
                String coverOriginalFilename = coverFile.getOriginalFilename();
                String coverFilename;
                if (coverOriginalFilename == null || coverOriginalFilename.isBlank()) {
                    coverFilename = System.currentTimeMillis() + "-cover";
                } else {
                    coverFilename = coverOriginalFilename.replaceAll("\\s+", "-");
                }
                Files.createDirectories(Path.of(articleCoverImageDir));
                Path coverTarget = CmsUtil.resolveSecurePath(articleCoverImageDir, coverFilename);

                int suffix = 1;
                String base = coverFilename;
                String ext = "";
                int dot = coverFilename.lastIndexOf('.');
                if (dot > 0) {
                    base = coverFilename.substring(0, dot);
                    ext = coverFilename.substring(dot);
                }
                while (Files.exists(coverTarget)) {
                    coverFilename = base + "-" + suffix + ext;
                    coverTarget = CmsUtil.resolveSecurePath(articleCoverImageDir, coverFilename);
                    suffix++;
                }

                if (a.getCoverImageFilename() != null) {
                    try { Path old = CmsUtil.resolveSecurePath(articleCoverImageDir, a.getCoverImageFilename()); Files.deleteIfExists(old); } catch (IOException ignored) {}
                }

                try {
                    BufferedImage original = ImageIO.read(coverFile.getInputStream());
                    if (original != null) {
                        BufferedImage resized = resizeImageIfNeeded(original, articleProperties.getCoverImage().getUpload().getResize().getMaxSize());
                        String writeExt = "png";
                        if (dot > 0 && dot < coverFilename.length() - 1) writeExt = coverFilename.substring(dot + 1).toLowerCase();
                        ImageIO.write(resized, writeExt, coverTarget.toFile());
                    } else {
                        Files.copy(coverFile.getInputStream(), coverTarget, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    Files.copy(coverFile.getInputStream(), coverTarget, StandardCopyOption.REPLACE_EXISTING);
                }

                a.setCoverImageFilename(coverFilename);
            } else if (request.getCoverImageOriginalUrl() != null && !request.getCoverImageOriginalUrl().isBlank()) {
                // download remote image and replace only if the original URL changed
                String url = request.getCoverImageOriginalUrl();
                // if the URL is identical to the existing one, skip downloading
                if (url.equals(existingCoverOriginalUrl)) {
                    // nothing to do — keep existing cover image file and filename
                } else {
                    String coverFilename = request.getCoverImageFilename();
                    if (coverFilename == null || coverFilename.isBlank()) {
                        try {
                            java.net.URL u = new java.net.URL(url);
                            String p = u.getPath();
                            int last = p.lastIndexOf('/');
                            String lastSeg = last >= 0 ? p.substring(last + 1) : p;
                            if (lastSeg == null || lastSeg.isBlank()) lastSeg = System.currentTimeMillis() + "-cover";
                            coverFilename = lastSeg.replaceAll("\\s+", "-");
                        } catch (Exception ex) {
                            coverFilename = System.currentTimeMillis() + "-cover";
                        }
                    }

                    Files.createDirectories(Path.of(articleCoverImageDir));
                    Path coverTarget = CmsUtil.resolveSecurePath(articleCoverImageDir, coverFilename);

                    int suffix = 1;
                    String base = coverFilename;
                    String ext = "";
                    int dot = coverFilename.lastIndexOf('.');
                    if (dot > 0) {
                        base = coverFilename.substring(0, dot);
                        ext = coverFilename.substring(dot);
                    }
                    while (Files.exists(coverTarget)) {
                        coverFilename = base + "-" + suffix + ext;
                        coverTarget = CmsUtil.resolveSecurePath(articleCoverImageDir, coverFilename);
                        suffix++;
                    }

                    if (a.getCoverImageFilename() != null) {
                        try { Path old = CmsUtil.resolveSecurePath(articleCoverImageDir, a.getCoverImageFilename()); Files.deleteIfExists(old); } catch (IOException ignored) {}
                    }

                    try (java.io.InputStream is = new java.net.URL(url).openStream()) {
                        try {
                            byte[] data = is.readAllBytes();
                            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(data);
                            BufferedImage original = ImageIO.read(bis);
                            if (original != null) {
                                BufferedImage resized = resizeImageIfNeeded(original, articleProperties.getCoverImage().getUpload().getResize().getMaxSize());
                                String writeExt = "png";
                                if (dot > 0 && dot < coverFilename.length() - 1) writeExt = coverFilename.substring(dot + 1).toLowerCase();
                                ImageIO.write(resized, writeExt, coverTarget.toFile());
                            } else {
                                Files.write(coverTarget, data);
                            }
                        } catch (IOException ex) {
                            try (java.io.InputStream is2 = new java.net.URL(url).openStream()) {
                                Files.copy(is2, coverTarget, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }

                    a.setCoverImageFilename(coverFilename);
                    a.setCoverImageOriginalUrl(request.getCoverImageOriginalUrl());
                }
            }

            // handle cover image filename change only (rename existing file)
            if (request.getCoverImageFilename() != null &&
                    request.getCoverImageFilename().lastIndexOf('.') > 0 &&
                    !request.getCoverImageFilename().equals(a.getCoverImageFilename())) {
                // change the image file name in local storage only (no re-download), implying a rename
                String renameDir = articleProperties.getCoverImage().getUpload().getDirectory();
                Path oldPath = CmsUtil.resolveSecurePath(renameDir, a.getCoverImageFilename());
                Path newPath = CmsUtil.resolveSecurePath(renameDir, request.getCoverImageFilename());
                // if newPath exists, it will not be overwritten
                if (Files.exists(newPath)) {
                    throw new IllegalArgumentException("Cover image file with name " + request.getCoverImageFilename() + " already exists");
                }
                
                if (Files.exists(oldPath)) {
                    Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                }

                a.setCoverImageFilename(request.getCoverImageFilename());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to save cover image: " + e.getMessage());
        }

        if (request.getTags() != null) a.setTags(request.getTags());
        if (request.getLang() != null) a.setLang(request.getLang());
        if (request.getDisplayOrder() != null) a.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsActive() != null) a.setIsActive(request.getIsActive());

        a.setUpdatedBy(userId);
        Article saved = articleRepository.save(a);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ArticleResponse getArticleById(String id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", "id", id));
        return toResponse(article);
    }

    @Transactional(readOnly = true)
    public List<ArticleResponse> listArticles() {
        List<Article> all = articleRepository.findAll();
        return all.stream().map(this::toResponse).toList();
    }

    public void deleteArticle(String id, String userId) {
        Article a = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", "id", id));
        a.setIsActive(false);
        a.setUpdatedBy(userId);
        articleRepository.save(a);
    }

    @Transactional(readOnly = true)
    public java.io.File getCoverImageFileByFilename(String filename) {
        if (filename == null) throw new IllegalArgumentException("filename is null");
        Path coverPath = CmsUtil.resolveSecurePath(articleProperties.getCoverImage().getUpload().getDirectory(), filename);
        if (!Files.exists(coverPath)) {
            throw new ResourceNotFoundException("Cover image", "filename", filename);
        }
        return coverPath.toFile();
    }

    @Transactional(readOnly = true)
    public Page<ArticleResponse> searchArticles(String title, Article.Language lang, String tags, Boolean isActive, Pageable pageable) {
        Page<Article> page = articleRepository.searchArticles(title, lang, tags, isActive, pageable);
        return page.map(this::toResponse);
    }

    private ArticleResponse toResponse(Article a) {
        return ArticleResponse.from(a);
    }

    private BufferedImage resizeImageIfNeeded(BufferedImage image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= maxSize && height <= maxSize) return image;
        float scale = Math.min((float) maxSize / width, (float) maxSize / height);
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);
        int type = image.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : image.getType();
        BufferedImage resized = new BufferedImage(newWidth, newHeight, type);
        java.awt.Graphics2D g2d = resized.createGraphics();
        try {
            g2d.drawImage(image, 0, 0, newWidth, newHeight, null);
        } finally {
            g2d.dispose();
        }
        return resized;
    }
}
