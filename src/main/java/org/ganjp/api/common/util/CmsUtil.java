package org.ganjp.api.common.util;

import java.nio.file.Path;

public class CmsUtil {
    private CmsUtil() {}

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
}
