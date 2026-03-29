package org.ganjp.api.common.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CmsUtil {
    private CmsUtil() {}

    private static final DateTimeFormatter DELETED_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Move a file to a {@code deleted/} subfolder under its parent directory
     * instead of physically deleting it. A timestamp is appended to the filename
     * to prevent collisions (e.g. {@code photo.jpg → deleted/photo_20260329-122117.jpg}).
     *
     * <p>The {@code deleted/} directory is auto-created if it does not exist.
     * If the move operation fails, the method falls back to
     * {@link Files#deleteIfExists(Path)}.
     *
     * @param sourceFile the file to move (must be an absolute, normalised path)
     */
    public static void moveToDeletedFolder(Path sourceFile) {
        if (sourceFile == null || !Files.exists(sourceFile)) {
            return;
        }
        try {
            Path deletedDir = sourceFile.getParent().resolve("deleted");
            Files.createDirectories(deletedDir);

            String originalName = sourceFile.getFileName().toString();
            String timestamp = LocalDateTime.now().format(DELETED_TIMESTAMP_FORMAT);

            String baseName;
            String extension;
            int dot = originalName.lastIndexOf('.');
            if (dot > 0) {
                baseName = originalName.substring(0, dot);
                extension = originalName.substring(dot); // includes the dot
            } else {
                baseName = originalName;
                extension = "";
            }

            Path target = deletedDir.resolve(baseName + "_" + timestamp + extension);

            // Handle unlikely collision (same file deleted twice in same second)
            int suffix = 1;
            while (Files.exists(target)) {
                target = deletedDir.resolve(baseName + "_" + timestamp + "_" + suffix + extension);
                suffix++;
            }

            Files.move(sourceFile, target, StandardCopyOption.ATOMIC_MOVE);
            log.info("Moved deleted file to trash: {} → {}", sourceFile.getFileName(), target.getFileName());
        } catch (IOException e) {
            log.warn("Failed to move file to deleted folder, falling back to delete: {}", sourceFile, e);
            try {
                Files.deleteIfExists(sourceFile);
            } catch (IOException ex) {
                log.error("Failed to delete file: {}", sourceFile, ex);
            }
        }
    }

    /**
     * Resolve a filename against a base directory, preventing path traversal attacks.
     * Validates that the resolved path stays within the base directory.
     * @param baseDir The base directory
     * @param filename The filename to resolve
     * @return The resolved, normalized path
     * @throws IllegalArgumentException if the resolved path escapes the base directory
     */
    public static Path resolveSecurePath(String baseDir, String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename must not be blank");
        }
        Path base = Path.of(baseDir).toAbsolutePath().normalize();
        Path resolved = base.resolve(filename).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Invalid filename: path traversal detected");
        }
        return resolved;
    }

    /**
     * Sanitize a filename for use in Content-Disposition headers.
     * Removes characters that could enable header injection.
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "download";
        return filename.replaceAll("[\"\\r\\n\\\\/:*?<>|]", "_");
    }

    /**
     * Get file extension from filename
     * @param filename The filename
     * @return The extension (without dot) or empty string
     */
    public static String getFileExtension(String filename) {
        if (filename == null) return "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "";
    }

    /**
     * Determine content type based on file extension
     * @param filename The filename to check
     * @return Content type string (e.g., "image/png", "image/svg+xml")
     */
    public static String determineContentType(String filename) {
        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".png")) {
            return "image/png";
        } else if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerFilename.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerFilename.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (lowerFilename.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerFilename.endsWith(".bmp")) {
            return "image/bmp";
        } else if (lowerFilename.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lowerFilename.endsWith(".webm")) {
            return "video/webm";
        } else if (lowerFilename.endsWith(".ogv") || lowerFilename.endsWith(".ogg")) {
            return "video/ogg";
        } else if (lowerFilename.endsWith(".mov")) {
            return "video/quicktime";
        } else if (lowerFilename.endsWith(".mkv")) {
            return "video/x-matroska";
        } else {
            return "application/octet-stream"; // fallback
        }
    }

    // ── YouTube utilities ─────────────────────────────────────────────

    /**
     * Pattern matching various YouTube URL formats:
     * - https://www.youtube.com/watch?v=VIDEO_ID
     * - https://youtu.be/VIDEO_ID
     * - https://www.youtube.com/embed/VIDEO_ID
     * - https://www.youtube.com/v/VIDEO_ID
     * - https://www.youtube.com/shorts/VIDEO_ID
     * - https://m.youtube.com/watch?v=VIDEO_ID
     */
    private static final Pattern YOUTUBE_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?.*v=|embed/|v/|shorts/)|youtu\\.be/)([a-zA-Z0-9_-]{11})");

    /**
     * Check whether a URL is a YouTube video URL.
     */
    public static boolean isYouTubeUrl(String url) {
        if (url == null || url.isBlank()) return false;
        return YOUTUBE_PATTERN.matcher(url).find();
    }

    /**
     * Extract the 11-character video ID from a YouTube URL.
     * @return the video ID, or null if the URL is not a recognised YouTube format
     */
    public static String extractYouTubeVideoId(String url) {
        if (url == null || url.isBlank()) return null;
        Matcher matcher = YOUTUBE_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Build a YouTube embed URL from a video ID.
     * Example: https://www.youtube.com/embed/dQw4w9WgXcQ
     */
    public static String buildYouTubeEmbedUrl(String videoId) {
        if (videoId == null) return null;
        return "https://www.youtube.com/embed/" + videoId;
    }

    /**
     * Build a YouTube thumbnail URL (best available quality).
     * Falls back to hqdefault if maxresdefault is not available for the video.
     */
    public static String buildYouTubeThumbnailUrl(String videoId) {
        if (videoId == null) return null;
        return "https://img.youtube.com/vi/" + videoId + "/maxresdefault.jpg";
    }

    /**
     * Build a YouTube standard-quality thumbnail URL (always available).
     */
    public static String buildYouTubeThumbnailUrlHq(String videoId) {
        if (videoId == null) return null;
        return "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
    }

    // ── WebP conversion utilities ──────────────────────────────────────

    /**
     * Check whether a file is WebP based on its content type or filename extension.
     */
    public static boolean isWebp(String contentType, String filename) {
        if (contentType != null && contentType.contains("webp")) return true;
        if (filename != null && filename.toLowerCase().endsWith(".webp")) return true;
        return false;
    }

    /**
     * Check whether an extension string represents WebP.
     */
    public static boolean isWebpExtension(String extension) {
        return "webp".equalsIgnoreCase(extension);
    }

    /**
     * Determine the best output format for a WebP image based on transparency.
     * @return "png" if the image has transparent pixels, "jpg" otherwise
     */
    public static String resolveWebpOutputFormat(BufferedImage image) {
        return hasTransparency(image) ? "png" : "jpg";
    }

    /**
     * Check whether a BufferedImage contains any transparent (non-opaque) pixels.
     */
    public static boolean hasTransparency(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) return false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = (image.getRGB(x, y) >> 24) & 0xFF;
                if (alpha < 255) return true;
            }
        }
        return false;
    }

    /**
     * Prepare a BufferedImage for the given output format.
     * For PNG: ensures ARGB colour model to preserve transparency.
     * For JPG: strips alpha channel and fills with white background.
     */
    public static BufferedImage prepareForOutput(BufferedImage image, String targetFormat) {
        if ("png".equalsIgnoreCase(targetFormat)) {
            // Ensure ARGB for PNG
            if (image.getType() == BufferedImage.TYPE_INT_ARGB
                    || image.getType() == BufferedImage.TYPE_4BYTE_ABGR) {
                return image;
            }
            BufferedImage converted = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = converted.createGraphics();
            try {
                g.drawImage(image, 0, 0, null);
            } finally {
                g.dispose();
            }
            return converted;
        } else {
            // JPG: strip alpha, white background
            BufferedImage rgb = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                g.drawImage(image, 0, 0, null);
            } finally {
                g.dispose();
            }
            return rgb;
        }
    }

    /**
     * Replace the extension part of a filename.
     */
    public static String replaceExtension(String filename, String newExtension) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        if (dot == -1) return filename + "." + newExtension;
        return filename.substring(0, dot + 1) + newExtension;
    }
}
