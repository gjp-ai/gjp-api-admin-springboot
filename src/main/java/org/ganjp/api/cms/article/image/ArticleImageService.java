package org.ganjp.api.cms.article.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.cms.article.ArticleProperties;
import org.ganjp.api.cms.article.image.ArticleImageCreateRequest;
import org.ganjp.api.cms.article.image.ArticleImageUpdateRequest;
import org.ganjp.api.cms.article.image.ArticleImageResponse;
import org.ganjp.api.cms.article.image.ArticleImage;
import org.ganjp.api.cms.article.image.ArticleImageRepository;
import org.ganjp.api.common.util.CmsUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleImageService {
    private final ArticleImageRepository articleImageRepository;
    private final ArticleProperties articleProperties;

    public org.springframework.core.io.Resource getImage(String filename) {
        try {
            Path uploadPath = Paths.get(articleProperties.getContentImage().getUpload().getDirectory());
            Path filePath = uploadPath.resolve(filename);
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read the file!");
            }
        } catch (java.net.MalformedURLException e) {
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }

    public java.io.File getImageFile(String filename) {
        Path uploadPath = Paths.get(articleProperties.getContentImage().getUpload().getDirectory());
        Path filePath = uploadPath.resolve(filename);
        return filePath.toFile();
    }

    public ArticleImageResponse getArticleImageById(String id) {
        Optional<ArticleImage> imageOpt = articleImageRepository.findByIdAndIsActiveTrue(id);
        return imageOpt.map(this::toResponse).orElse(null);
    }

    public List<ArticleImageResponse> listArticleImages(String articleId) {
        List<ArticleImage> images = articleImageRepository.findByArticleIdAndIsActiveTrueOrderByDisplayOrderAsc(articleId);
        return images.stream().map(this::toResponse).toList();
    }

    public ArticleImageResponse createArticleImage(ArticleImageCreateRequest request, String userId) {
        try {
            if (request.getFilename() == null || request.getFilename().trim().isEmpty()) {
                throw new IllegalArgumentException("Filename is required");
            }

            String targetFilename = request.getFilename();
            String targetExtension = CmsUtil.getFileExtension(targetFilename);
            
            BufferedImage bufferedImage;
            String sourceExtension;

            if (request.getFile() != null && !request.getFile().isEmpty()) {
                MultipartFile file = request.getFile();
                String originalFilename = file.getOriginalFilename();
                sourceExtension = CmsUtil.getFileExtension(originalFilename);
                bufferedImage = ImageIO.read(file.getInputStream());
            } else if (request.getOriginalUrl() != null && !request.getOriginalUrl().trim().isEmpty()) {
                java.net.URL url = new java.net.URL(request.getOriginalUrl());
                bufferedImage = ImageIO.read(url);
                String path = url.getPath();
                sourceExtension = CmsUtil.getFileExtension(path);
            } else {
                 throw new IllegalArgumentException("File or Original URL is required");
            }

            if (bufferedImage == null) {
                 throw new IllegalArgumentException("Failed to read image data");
            }

            String finalExtension;
            if (targetExtension != null && !targetExtension.isEmpty()) {
                finalExtension = targetExtension;
            } else {
                if (sourceExtension != null && !sourceExtension.isEmpty()) {
                    finalExtension = sourceExtension;
                } else {
                    finalExtension = "png";
                }
                targetFilename = targetFilename + "." + finalExtension;
            }

            Path uploadPath = Paths.get(articleProperties.getContentImage().getUpload().getDirectory());
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(targetFilename);
            if (Files.exists(filePath)) {
                throw new IllegalArgumentException("File with name " + targetFilename + " already exists");
            }
            ImageIO.write(bufferedImage, finalExtension, filePath.toFile());
            
            Integer width = bufferedImage.getWidth();
            Integer height = bufferedImage.getHeight();

            ArticleImage articleImage = ArticleImage.builder()
                    .id(UUID.randomUUID().toString())
                    .articleId(request.getArticleId())
                    .articleTitle(request.getArticleTitle())
                    .filename(targetFilename)
                    .originalUrl(request.getOriginalUrl())
                    .width(width)
                    .height(height)
                    .lang(request.getLang())
                    .displayOrder(request.getDisplayOrder())
                    .isActive(request.getIsActive())
                    .createdAt(new Timestamp(System.currentTimeMillis()))
                    .updatedAt(new Timestamp(System.currentTimeMillis()))
                    .createdBy(userId)
                    .updatedBy(userId)
                    .build();

            ArticleImage saved = articleImageRepository.save(articleImage);
            return toResponse(saved);
        } catch (IOException e) {
            log.error("Error creating article image", e);
            throw new java.io.UncheckedIOException("Failed to store file", e);
        }
    }

    public ArticleImageResponse updateArticleImage(String id, ArticleImageUpdateRequest request, String userId) {
        Optional<ArticleImage> imageOpt = articleImageRepository.findByIdAndIsActiveTrue(id);
        if (imageOpt.isEmpty()) return null;
        ArticleImage image = imageOpt.get();

        if (request.getArticleId() != null) image.setArticleId(request.getArticleId());
        if (request.getArticleTitle() != null) image.setArticleTitle(request.getArticleTitle());
        if (request.getOriginalUrl() != null) image.setOriginalUrl(request.getOriginalUrl());
        if (request.getLang() != null) image.setLang(request.getLang());
        if (request.getDisplayOrder() != null) image.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsActive() != null) image.setIsActive(request.getIsActive());

        image.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        image.setUpdatedBy(userId);

        ArticleImage saved = articleImageRepository.save(image);
        return toResponse(saved);
    }

    public void deleteArticleImage(String id) {
        Optional<ArticleImage> imageOpt = articleImageRepository.findById(id);
        if (imageOpt.isPresent()) {
            ArticleImage image = imageOpt.get();
            // Soft delete
            image.setIsActive(false);
            articleImageRepository.save(image);
        }
    }

    public void deleteArticleImagePermanently(String id) {
        Optional<ArticleImage> imageOpt = articleImageRepository.findById(id);
        if (imageOpt.isPresent()) {
            ArticleImage image = imageOpt.get();
            
            // Delete file
            if (image.getFilename() != null) {
                try {
                    Path uploadPath = Paths.get(articleProperties.getContentImage().getUpload().getDirectory());
                    Path filePath = uploadPath.resolve(image.getFilename());
                    Files.deleteIfExists(filePath);
                } catch (IOException e) {
                    log.error("Failed to delete file for article image: " + id, e);
                }
            }
            
            articleImageRepository.delete(image);
        }
    }
    
    public List<ArticleImageResponse> searchArticleImages(String articleId, ArticleImage.Language lang, Boolean isActive) {
        return articleImageRepository.searchArticleImages(articleId, lang, isActive)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private int[] getImageDimensions(Path filePath) {
        try {
            BufferedImage img = ImageIO.read(filePath.toFile());
            if (img != null) {
                return new int[]{img.getWidth(), img.getHeight()};
            }
        } catch (Exception e) {
            log.warn("Could not read image dimensions for {}", filePath);
        }
        return new int[0];
    }

    private ArticleImageResponse toResponse(ArticleImage image) {
        String fileUrl = null;
        if (image.getFilename() != null) {
            fileUrl = articleProperties.getContentImage().getBaseUrl() + "/" + image.getFilename();
        }
        return ArticleImageResponse.builder()
                .id(image.getId())
                .articleId(image.getArticleId())
                .articleTitle(image.getArticleTitle())
                .filename(image.getFilename())
                .fileUrl(fileUrl)
                .originalUrl(image.getOriginalUrl())
                .width(image.getWidth())
                .height(image.getHeight())
                .lang(image.getLang())
                .displayOrder(image.getDisplayOrder())
                .createdBy(image.getCreatedBy())
                .updatedBy(image.getUpdatedBy())
                .isActive(image.getIsActive())
                .createdAt(image.getCreatedAt().toString())
                .updatedAt(image.getUpdatedAt().toString())
                .build();
    }
}
