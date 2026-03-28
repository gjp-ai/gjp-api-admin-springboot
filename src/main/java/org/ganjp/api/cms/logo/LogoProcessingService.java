package org.ganjp.api.cms.logo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import org.ganjp.api.common.util.CmsUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Service for processing logo images
 * Handles upload, download from URL, and resize to 256px
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogoProcessingService {

    private final LogoUploadProperties uploadProperties;
    public LogoUploadProperties getUploadProperties() {
        return uploadProperties;
    }
        /**
         * Convert image file to a new format (SVG to PNG/JPG, PNG to JPG, etc.)
         * @param sourceFile The source image file
         * @param targetExtension The target extension (e.g., png, jpg)
         * @param logoName The logo name for filename generation
         * @return The new filename if successful, null if failed
         */
    public String convertImageFormat(File sourceFile, String targetExtension, String logoName) {
        String sourceExtension = getFileExtension(sourceFile.getName());
        String newFilename = generateFilename(logoName, targetExtension);
        Path newPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), newFilename);
        try {
            if ("svg".equalsIgnoreCase(sourceExtension) && !"svg".equalsIgnoreCase(targetExtension)) {
                // Only allow SVG to PNG directly
                if ("png".equalsIgnoreCase(targetExtension)) {
                    try (InputStream svgInputStream = new FileInputStream(sourceFile);
                         OutputStream pngOutputStream = new FileOutputStream(newPath.toFile())) {
                        org.apache.batik.transcoder.image.PNGTranscoder transcoder = new org.apache.batik.transcoder.image.PNGTranscoder();
                        org.apache.batik.transcoder.TranscoderInput input = new org.apache.batik.transcoder.TranscoderInput(svgInputStream);
                        org.apache.batik.transcoder.TranscoderOutput output = new org.apache.batik.transcoder.TranscoderOutput(pngOutputStream);
                        transcoder.transcode(input, output);
                    }
                } else if ("jpg".equalsIgnoreCase(targetExtension) || "jpeg".equalsIgnoreCase(targetExtension)) {
                    // Convert SVG to PNG first, then PNG to JPG
                    String tempPngFilename = generateFilename(logoName, "png");
                    Path tempPngPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), tempPngFilename);
                    try (InputStream svgInputStream = new FileInputStream(sourceFile);
                         OutputStream pngOutputStream = new FileOutputStream(tempPngPath.toFile())) {
                        org.apache.batik.transcoder.image.PNGTranscoder transcoder = new org.apache.batik.transcoder.image.PNGTranscoder();
                        org.apache.batik.transcoder.TranscoderInput input = new org.apache.batik.transcoder.TranscoderInput(svgInputStream);
                        org.apache.batik.transcoder.TranscoderOutput output = new org.apache.batik.transcoder.TranscoderOutput(pngOutputStream);
                        transcoder.transcode(input, output);
                    }
                    // Now convert PNG to JPG
                    BufferedImage pngImage = ImageIO.read(tempPngPath.toFile());
                    if (pngImage == null) {
                        throw new IOException("Unable to read intermediate PNG file for SVG to JPG conversion: " + tempPngFilename);
                    }
                    ImageIO.write(pngImage, targetExtension, newPath.toFile());
                    // Delete temp PNG file
                    Files.deleteIfExists(tempPngPath);
                } else {
                    log.error("Unsupported target extension for SVG conversion: {}", targetExtension);
                    return null;
                }
            } else {
                // Raster to raster conversion (PNG <-> JPG <-> BMP <-> WEBP)
                BufferedImage image = ImageIO.read(sourceFile);
                if (image == null) {
                    throw new IOException("Unable to read image file for conversion: " + sourceFile.getName());
                }
                ImageIO.write(image, targetExtension, newPath.toFile());
            }
            log.info("Converted image {} to format {} as {}", sourceFile.getName(), targetExtension, newFilename);
            return newFilename;
        } catch (Exception e) {
            log.error("Error converting image format from {} to {}: {}", sourceFile.getName(), targetExtension, e.getMessage());
            return null;
        }
    }

    /**
     * Process uploaded file: resize to 256px and save (skip resize for SVG)
     * @param file Uploaded file
     * @param logoName Logo name for filename generation
     * @return Processed image info
     */
    public ProcessedImage processUploadedFile(MultipartFile file, String logoName) throws IOException {
        log.debug("Processing uploaded file: {}", file.getOriginalFilename());
        
        // Validate file
        validateFile(file);
        
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        
        // Check if SVG - handle differently (no resize needed)
        if ("svg".equalsIgnoreCase(extension)) {
            return saveSvgFile(file.getInputStream(), extension, null, logoName);
        }
        
        // Read image from multipart file
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) {
            throw new IOException("Unable to read image file");
        }
        
        return resizeAndSave(originalImage, extension, null, logoName);
    }

    /**
     * Process image from URL: download, resize to 256px and save (skip resize for SVG)
     * @param imageUrl URL of the image
     * @param logoName Logo name for filename generation
     * @return Processed image info
     */
    public ProcessedImage processImageFromUrl(String imageUrl, String logoName) throws IOException {
        log.debug("Processing image from URL: {}", imageUrl);
        
        // Extract extension from URL
        String extension = getFileExtensionFromUrl(imageUrl);
        
        // Check if SVG - handle differently (no resize needed)
        if ("svg".equalsIgnoreCase(extension)) {
            java.net.URI uri = java.net.URI.create(imageUrl);
            try (InputStream inputStream = uri.toURL().openStream()) {
                return saveSvgFile(inputStream, extension, imageUrl, logoName);
            }
        }
        
        // Download raster image from URL
        java.net.URI uri = java.net.URI.create(imageUrl);
        BufferedImage originalImage = ImageIO.read(uri.toURL());
        
        if (originalImage == null) {
            throw new IOException("Unable to read image from URL: " + imageUrl);
        }
        
        return resizeAndSave(originalImage, extension, imageUrl, logoName);
    }

    /**
     * Resize image to 256px (width or height) and save
     */
    private ProcessedImage resizeAndSave(BufferedImage originalImage, String extension, String originalUrl, String logoName) throws IOException {
        int targetSize = uploadProperties.getResize().getTargetSize();
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        log.debug("Original image size: {}x{}", originalWidth, originalHeight);
        
        // Calculate new dimensions maintaining aspect ratio
        int newWidth;
        int newHeight;
        if (originalWidth > originalHeight) {
            // Landscape: set width to 256
            newWidth = targetSize;
            newHeight = (int) ((double) originalHeight / originalWidth * targetSize);
        } else {
            // Portrait or square: set height to 256
            newHeight = targetSize;
            newWidth = (int) ((double) originalWidth / originalHeight * targetSize);
        }
        
        log.debug("Resized image size: {}x{}", newWidth, newHeight);
        
        // Generate unique filename
        String filename = generateFilename(logoName, extension);
        
        // Ensure upload directory exists
        Path uploadDir = Paths.get(uploadProperties.getDirectory());
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
            log.info("Created upload directory: {}", uploadDir);
        }

        // Full path to save file
        Path fullPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), filename);

        // Resize and save image
        Thumbnails.of(originalImage)
                .size(newWidth, newHeight)
                .outputFormat(extension)
                .toFile(fullPath.toFile());
        
        log.info("Image saved: {}", fullPath);
        
        return ProcessedImage.builder()
                .filename(filename)
                .extension(extension)
                .originalUrl(originalUrl)
                .build();
    }

    /**
     * Save SVG file without resizing
     */
    private ProcessedImage saveSvgFile(InputStream inputStream, String extension, String originalUrl, String logoName) throws IOException {
        log.debug("Saving SVG file without resize");
        
        // Generate unique filename
        String filename = generateFilename(logoName, extension);
        
        // Ensure upload directory exists
        Path uploadDir = Paths.get(uploadProperties.getDirectory());
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
            log.info("Created upload directory: {}", uploadDir);
        }

        // Full path to save file
        Path fullPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), filename);

        // Copy SVG file directly (no resize needed)
        Files.copy(inputStream, fullPath, StandardCopyOption.REPLACE_EXISTING);
        
        log.info("SVG file saved: {}", fullPath);
        
        return ProcessedImage.builder()
                .filename(filename)
                .extension(extension)
                .originalUrl(originalUrl)
                .build();
    }

    /**
     * Rename logo file in storage when logo name changes
     * @param oldFilename Current filename in storage
     * @param newLogoName New logo name
     * @param extension File extension
     * @return New filename if successful, null if failed
     */
    public String renameLogoFile(String oldFilename, String newLogoName, String extension) {
        try {
            Path oldPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), oldFilename);

            if (!Files.exists(oldPath)) {
                log.warn("Logo file not found for renaming: {}", oldPath);
                return null;
            }

            // Generate new filename based on new logo name
            String newFilename = generateFilename(newLogoName, extension);
            Path newPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), newFilename);
            
            // Rename the file
            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Renamed logo file from {} to {}", oldFilename, newFilename);
            
            return newFilename;
        } catch (IOException e) {
            log.error("Error renaming logo file from {} with new name: {}", oldFilename, newLogoName, e);
            return null;
        }
    }

    /**
     * Get logo file from storage
     * @param filename The filename to retrieve
     * @return File object representing the logo file
     * @throws IOException if file not found or error reading file
     */
    public File getLogoFile(String filename) throws IOException {
        Path fullPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), filename);
        
        if (!Files.exists(fullPath)) {
            throw new IOException("Logo file not found: " + filename);
        }
        
        File file = fullPath.toFile();
        if (!file.isFile() || !file.canRead()) {
            throw new IOException("Cannot read logo file: " + filename);
        }
        
        log.debug("Retrieved logo file: {}", fullPath);
        return file;
    }

    /**
     * Delete logo file from storage
     */
    public void deleteLogoFile(String filename) {
        try {
            Path fullPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), filename);
            
            if (Files.exists(fullPath)) {
                Files.delete(fullPath);
                log.info("Deleted logo file: {}", fullPath);
            } else {
                log.warn("Logo file not found for deletion: {}", fullPath);
            }
        } catch (IOException e) {
            log.error("Error deleting logo file: {}", filename, e);
            // Don't throw exception, just log the error
        }
    }

    /**
     * Validate uploaded file
     */
    private void validateFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Uploaded file is empty");
        }
        
        if (file.getSize() > uploadProperties.getMaxFileSize()) {
            throw new IOException("File size exceeds maximum allowed: " + uploadProperties.getMaxFileSize() + " bytes");
        }
        
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("image/svg+xml"))) {
            throw new IOException("File must be an image (including SVG)");
        }
    }

    /**
     * Generate filename based on logo name with targetSize and timestamp
     * Rules:
     * - Convert to lowercase
     * - Replace spaces with underscores
     * - Convert Chinese characters to Pinyin with underscores
     * - Add targetSize suffix _256px (or configured size)
     * - Add timestamp suffix _yyyyMMddHHmmss
     * Example: "My Logo" -> "my_logo_256px_20231015143022.png"
     * Example: "我的标志" -> "wo_de_biao_zhi_256px_20231015143022.png"
     */
    private String generateFilename(String logoName, String extension) {
        String processedName = convertToFilename(logoName);
        int targetSize = uploadProperties.getResize().getTargetSize();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return processedName + "_" + targetSize + "_" + timestamp + "." + extension;
    }
    
    /**
     * Convert logo name to filename-safe format
     * - Convert Chinese to Pinyin
     * - Convert to lowercase
     * - Replace spaces with underscores
     * - Remove special characters
     */
    private String convertToFilename(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "logo";
        }
        
        StringBuilder result = new StringBuilder();
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
        
        for (char c : name.toCharArray()) {
            if (Character.toString(c).matches("[\\u4E00-\\u9FA5]")) {
                // Chinese character - convert to Pinyin
                try {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        if (result.length() > 0 && result.charAt(result.length() - 1) != '_') {
                            result.append('_');
                        }
                        result.append(pinyinArray[0]);
                    }
                } catch (Exception e) {
                    log.warn("Failed to convert Chinese character to Pinyin: {}", c);
                    // Skip this character
                }
            } else if (Character.isLetterOrDigit(c)) {
                // English letter or digit
                if (result.length() > 0 && Character.isUpperCase(c) && result.charAt(result.length() - 1) != '_') {
                    // Add underscore before uppercase letter (for camelCase)
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else if (Character.isWhitespace(c) || c == '-' || c == '_') {
                // Space, hyphen, or underscore - convert to single underscore
                if (result.length() > 0 && result.charAt(result.length() - 1) != '_') {
                    result.append('_');
                }
            }
            // Other special characters are skipped
        }
        
        // Remove trailing underscores
        while (result.length() > 0 && result.charAt(result.length() - 1) == '_') {
            result.deleteCharAt(result.length() - 1);
        }
        
        // Return result or default if empty
        return result.length() > 0 ? result.toString() : "logo";
    }

    /**
     * Generate unique filename (legacy method - kept for backward compatibility)
     */
    private String generateUniqueFilename(String extension) {
        return UUID.randomUUID().toString() + "." + extension;
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "png"; // default extension
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * Get file extension from URL
     */
    private String getFileExtensionFromUrl(String url) {
        String path = url.split("\\?")[0]; // Remove query parameters
        if (path.contains(".")) {
            String ext = path.substring(path.lastIndexOf(".") + 1).toLowerCase();
            // Validate it's an image extension (including SVG)
            if (ext.matches("jpg|jpeg|png|gif|webp|bmp|svg")) {
                return ext;
            }
        }
        return "png"; // default extension
    }

    /**
     * Inner class to hold processed image information
     */
    @lombok.Data
    @lombok.Builder
    public static class ProcessedImage {
        private String filename;
        private String extension;
        private String originalUrl;
    }
}
