package org.ganjp.api.cms.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.cms.image.ImageUploadProperties;
import org.ganjp.api.cms.image.ImageCreateRequest;
import org.ganjp.api.cms.image.ImageUpdateRequest;
import org.ganjp.api.cms.image.ImageResponse;
import org.ganjp.api.cms.image.Image;
import org.ganjp.api.cms.image.ImageRepository;
import org.ganjp.api.common.util.CmsUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ImageService {
    private final ImageRepository imageRepository;
    private final ImageUploadProperties imageUploadProperties;

    public ImageResponse getImageById(String id) {
        Optional<Image> imageOpt = imageRepository.findByIdAndIsActiveTrue(id);
        return imageOpt.map(this::toResponse).orElse(null);
    }

    public List<ImageResponse> listImages() {
        List<Image> images = imageRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        return images.stream().map(this::toResponse).toList();
    }

    public ImageResponse updateImage(String id, ImageUpdateRequest request, String userId) {
        Optional<Image> imageOpt = imageRepository.findById(id);
        if (imageOpt.isEmpty()) return null;
        Image image = imageOpt.get();
        String oldExtension = image.getExtension();
        String oldFilename = image.getFilename();
        String oldThumbnail = image.getThumbnailFilename();
        if (request.getName() != null) image.setName(request.getName());
        if (request.getOriginalUrl() != null) image.setOriginalUrl(request.getOriginalUrl());
        if (request.getSourceName() != null) image.setSourceName(request.getSourceName());
        if (request.getExtension() != null && !request.getExtension().equalsIgnoreCase(oldExtension)) {
            // Need to convert stored files to new extension
            String newExt = request.getExtension().toLowerCase();
            try {
                Path uploadDir = Paths.get(imageUploadProperties.getDirectory());
                Path oldImagePath = uploadDir.resolve(oldFilename);
                Path oldThumbPath = uploadDir.resolve(oldThumbnail);

                String newFilename = request.getFilename() != null ? request.getFilename() : replaceExtension(oldFilename, newExt);
                String newThumbnail = request.getThumbnailFilename() != null ? request.getThumbnailFilename() : replaceExtension(oldThumbnail, newExt);
                Path newImagePath = uploadDir.resolve(newFilename);
                Path newThumbPath = uploadDir.resolve(newThumbnail);

                // Try to read and write using ImageIO (conversion)
                try {
                    BufferedImage img = ImageIO.read(oldImagePath.toFile());
                    if (img != null) {
                        ImageIO.write(img, newExt, newImagePath.toFile());
                    } else {
                        // fallback to copy/rename if ImageIO cannot read (e.g., SVG)
                        Files.copy(oldImagePath, newImagePath);
                    }
                } catch (Exception e) {
                    // fallback: move/rename file
                    Files.copy(oldImagePath, newImagePath);
                }

                try {
                    BufferedImage timg = ImageIO.read(oldThumbPath.toFile());
                    if (timg != null) {
                        ImageIO.write(timg, newExt, newThumbPath.toFile());
                    } else {
                        Files.copy(oldThumbPath, newThumbPath);
                    }
                } catch (Exception e) {
                    Files.copy(oldThumbPath, newThumbPath);
                }

                // Remove old files
                try { Files.deleteIfExists(oldImagePath); } catch (Exception ignored) {}
                try { Files.deleteIfExists(oldThumbPath); } catch (Exception ignored) {}

                // Update entity fields to new names/extension/mime/size
                image.setFilename(newFilename);
                image.setThumbnailFilename(newThumbnail);
                image.setExtension(newExt);
                image.setMimeType(CmsUtil.determineContentType(newFilename));
                try { image.setSizeBytes(Files.size(newImagePath)); } catch (Exception ignored) {}
            } catch (IOException e) {
                log.error("Failed to convert image files to new extension {} for image {}", request.getExtension(), id, e);
                throw new IllegalStateException("Failed to convert image files to new extension: " + e.getMessage(), e);
            }
        } else {
            // Extension not changed, but filename might have
            if (request.getFilename() != null && !request.getFilename().equals(oldFilename)) {
                try {
                    Path uploadDir = Paths.get(imageUploadProperties.getDirectory());
                    Path oldPath = uploadDir.resolve(oldFilename);
                    Path newPath = uploadDir.resolve(request.getFilename());
                    Files.move(oldPath, newPath);
                    image.setFilename(request.getFilename());
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to rename image file: " + e.getMessage(), e);
                }
            }
            if (request.getThumbnailFilename() != null && !request.getThumbnailFilename().equals(oldThumbnail)) {
                try {
                    Path uploadDir = Paths.get(imageUploadProperties.getDirectory());
                    Path oldPath = uploadDir.resolve(oldThumbnail);
                    Path newPath = uploadDir.resolve(request.getThumbnailFilename());
                    Files.move(oldPath, newPath);
                    image.setThumbnailFilename(request.getThumbnailFilename());
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to rename thumbnail file: " + e.getMessage(), e);
                }
            }
            if (request.getExtension() != null) {
                image.setExtension(request.getExtension());
            }
        }
        if (request.getMimeType() != null) image.setMimeType(request.getMimeType());
        if (request.getAltText() != null) image.setAltText(request.getAltText());
        if (request.getTags() != null) image.setTags(request.getTags());
        if (request.getLang() != null) image.setLang(request.getLang());
        if (request.getDisplayOrder() != null) image.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsActive() != null) image.setIsActive(request.getIsActive());
        image.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        image.setUpdatedBy(userId);
        imageRepository.save(image);
        return toResponse(image);
    }

    public boolean deleteImage(String id, String userId) {
        Optional<Image> imageOpt = imageRepository.findByIdAndIsActiveTrue(id);
        if (imageOpt.isEmpty()) return false;
        Image image = imageOpt.get();
        image.setIsActive(false);
        image.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        image.setUpdatedBy(userId);
        imageRepository.save(image);
        return true;
    }

    public Page<ImageResponse> searchImages(String keyword, Pageable pageable) {
        Page<Image> images = imageRepository.searchByNameContaining(keyword, pageable);
        return images.map(this::toResponse);
    }

    public Page<ImageResponse> searchImages(String name, Image.Language lang, String tags, Boolean isActive, Pageable pageable) {
        Page<Image> images = imageRepository.searchImages(name, lang, tags, isActive, pageable);
        return images.map(this::toResponse);
    }

    public ImageResponse createImage(ImageCreateRequest request, String userId) throws IOException {
        String id = UUID.randomUUID().toString();
        BufferedImage originalImage;
        String extension;
        MultipartFile file = request.getFile();
        if (file == null || file.isEmpty()) {
            if (request.getOriginalUrl() == null || request.getOriginalUrl().isBlank()) {
                throw new IllegalArgumentException("originalUrl is required if file is empty");
            }
            java.net.URL url = new java.net.URL(request.getOriginalUrl());
            try (var inputStream = url.openStream()) {
                originalImage = ImageIO.read(inputStream);
                if (request.getFilename() != null && request.getFilename().lastIndexOf('.') != -1) {
                    extension = request.getFilename().substring(request.getFilename().lastIndexOf('.') + 1).toLowerCase();
                } else {
                    String urlPath = url.getPath();
                    int dotIdx = urlPath.lastIndexOf('.');
                    extension = (dotIdx > 0 && dotIdx < urlPath.length() - 1) ? urlPath.substring(dotIdx + 1).toLowerCase() : "png";
                }
            }
        } else {
            originalImage = ImageIO.read(file.getInputStream());
            String contentType = file.getContentType();
            if (contentType != null && contentType.contains("jpeg")) {
                extension = "jpg";
            } else if (contentType != null && contentType.contains("png")) {
                extension = "png";
            } else if (contentType != null && contentType.contains("gif")) {
                extension = "gif";
            } else if (contentType != null && contentType.contains("webp")) {
                extension = "webp";
            } else {
                extension = "png";
            }
        }
        BufferedImage resizedImage = resizeImageIfNeeded(originalImage, imageUploadProperties.getResize().getMaxSize());
        BufferedImage thumbnailImage = resizeImageIfNeeded(originalImage, imageUploadProperties.getResize().getThumbnailSize());
        
        String filename;
        String thumbnailFilename;
        
        if (request.getFilename() != null && !request.getFilename().isBlank()) {
            filename = request.getFilename();
            String onlyFilename = filename;
            if (filename.lastIndexOf('.') != -1) {
                onlyFilename = filename.substring(0, filename.lastIndexOf('.'));
            }
            filename = generateFilename(onlyFilename, extension, resizedImage.getWidth(), resizedImage.getHeight());
            thumbnailFilename = generateFilename(onlyFilename, extension, thumbnailImage.getWidth(), thumbnailImage.getHeight());
        } else {
            filename = generateFilename(request.getName(), extension, resizedImage.getWidth(), resizedImage.getHeight());
            thumbnailFilename = generateFilename(request.getName(), extension, thumbnailImage.getWidth(), thumbnailImage.getHeight());
        }

        Path imagePath = Paths.get(imageUploadProperties.getDirectory(), filename);
        Path thumbPath = Paths.get(imageUploadProperties.getDirectory(), thumbnailFilename);
        ImageIO.write(resizedImage, extension, imagePath.toFile());
        ImageIO.write(thumbnailImage, extension, thumbPath.toFile());

        Image image = new Image();
        image.setId(id);
        image.setName(request.getName());
        image.setOriginalUrl(request.getOriginalUrl());
        image.setSourceName(request.getSourceName());
        image.setFilename(filename);
        image.setThumbnailFilename(thumbnailFilename);
        image.setExtension(extension);
        image.setMimeType(CmsUtil.determineContentType(filename));
        image.setSizeBytes(Files.size(imagePath));
        image.setWidth(resizedImage.getWidth());
        image.setHeight(resizedImage.getHeight());
        image.setAltText(request.getAltText());
        image.setTags(request.getTags());
        image.setLang(request.getLang());
        image.setDisplayOrder(request.getDisplayOrder());
        image.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        image.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        image.setCreatedBy(userId);
        image.setUpdatedBy(userId);
        image.setIsActive(request.getIsActive() == null || request.getIsActive());
        imageRepository.save(image);
        return toResponse(image);
    }

    /**
     * Get image file by filename for viewing in browser
     * @param filename The filename to retrieve
     * @return File object representing the image file
     * @throws IOException if file not found or error reading file
     */
    public java.io.File getImageFileByFilename(String filename) throws IOException {
        // Validate that the filename exists in database for security
        List<Image> images = imageRepository.findAll();
        boolean filenameExists = images.stream()
                .anyMatch(image -> filename.equals(image.getFilename()));

        if (!filenameExists) {
            throw new IllegalArgumentException("Image not found with filename: " + filename);
        }

        return getImageFile(filename);
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
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(image, 0, 0, newWidth, newHeight, null);
        } finally {
            g2d.dispose();
        }
        return resized;
    }

    private String generateFilename(String name, String extension, int width, int height) {
        // Null-safe: if name is null or blank, fall back to 'img'
        String safeName;
        if (name == null || name.isBlank()) {
            safeName = "img";
        } else {
            // Convert Chinese characters to pinyin; leave ASCII/Latin characters intact
            String converted = convertToPinyin(name);
            safeName = converted.replaceAll("[^a-zA-Z0-9-_]", "-");
            if (safeName.isBlank()) safeName = "img";
        }
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return safeName + "-" + width + "-" + height + "-" + timestamp + "." + extension;
    }

    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();
    static {
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        PINYIN_FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    /**
     * Convert Chinese characters in the input string to pinyin. Non-Chinese characters are kept as-is.
     * Uses the first pinyin reading when multiple exist.
     */
    private String convertToPinyin(String input) {
        StringBuilder sb = new StringBuilder();
        for (char ch : input.toCharArray()) {
            if (isChinese(ch)) {
                try {
                    String[] pinyins = PinyinHelper.toHanyuPinyinStringArray(ch, PINYIN_FORMAT);
                    if (pinyins != null && pinyins.length > 0) {
                        sb.append(pinyins[0]);
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    log.debug("Pinyin conversion failed for char {}", ch, e);
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private boolean isChinese(char ch) {
        Character.UnicodeScript sc = Character.UnicodeScript.of(ch);
        return sc == Character.UnicodeScript.HAN;
    }

    private ImageResponse toResponse(Image image) {
        ImageResponse resp = new ImageResponse();
        resp.setId(image.getId());
        resp.setName(image.getName());
        resp.setOriginalUrl(image.getOriginalUrl());
        resp.setSourceName(image.getSourceName());
        resp.setFilename(image.getFilename());
        resp.setThumbnailFilename(image.getThumbnailFilename());
        resp.setExtension(image.getExtension());
        resp.setMimeType(image.getMimeType());
        resp.setSizeBytes(image.getSizeBytes());
        resp.setWidth(image.getWidth());
        resp.setHeight(image.getHeight());
        resp.setAltText(image.getAltText());
        resp.setTags(image.getTags());
        resp.setLang(image.getLang());
        resp.setDisplayOrder(image.getDisplayOrder());
        resp.setCreatedBy(image.getCreatedBy());
        resp.setUpdatedBy(image.getUpdatedBy());
        resp.setIsActive(image.getIsActive());
        resp.setCreatedAt(image.getCreatedAt() != null ? image.getCreatedAt().toString() : null);
        resp.setUpdatedAt(image.getUpdatedAt() != null ? image.getUpdatedAt().toString() : null);
        return resp;
    }

    /**
     * Get image file from storage
     * @param filename The filename to retrieve
     * @return File object representing the image file
     * @throws IOException if file not found or error reading file
     */
    public File getImageFile(String filename) throws IOException {
        Path uploadDir = Paths.get(imageUploadProperties.getDirectory());
        Path fullPath = uploadDir.resolve(filename);

        if (!Files.exists(fullPath)) {
            throw new IOException("Image file not found: " + filename);
        }

        File file = fullPath.toFile();
        if (!file.isFile() || !file.canRead()) {
            throw new IOException("Cannot read image file: " + filename);
        }

        log.debug("Retrieved image file: {}", fullPath);
        return file;
    }

    private String replaceExtension(String filename, String newExt) {
        int dot = filename.lastIndexOf('.');
        if (dot == -1) return filename + "." + newExt;
        return filename.substring(0, dot + 1) + newExt;
    }
}
