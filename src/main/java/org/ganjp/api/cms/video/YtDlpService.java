package org.ganjp.api.cms.video;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Service that wraps the yt-dlp CLI tool to download videos from YouTube
 * and other supported platforms.
 *
 * Requires {@code yt-dlp} to be installed and available on the system PATH.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YtDlpService {

    private static final long DOWNLOAD_TIMEOUT_SECONDS = 600; // 10 minutes
    private static final long METADATA_TIMEOUT_SECONDS = 30;

    /**
     * Metadata extracted from a video URL via yt-dlp.
     */
    @Data
    public static class VideoMetadata {
        private String title;
        private String extension;
        private Long durationSeconds;
        private String thumbnailUrl;
    }

    /**
     * Result of a yt-dlp download operation.
     */
    @Data
    public static class DownloadResult {
        private Path filePath;
        private String filename;
        private VideoMetadata metadata;
    }

    /**
     * Check whether yt-dlp is available on the system.
     */
    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("yt-dlp", "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                return true;
            }
        } catch (IOException | InterruptedException e) {
            // yt-dlp not found
        }
        return false;
    }

    /**
     * Extract metadata from a video URL without downloading.
     */
    public VideoMetadata extractMetadata(String url) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "--print", "%(title)s|||%(ext)s|||%(duration)s|||%(thumbnail)s",
                "--no-download",
                url
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.readLine();
        }

        try {
            boolean finished = process.waitFor(METADATA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("yt-dlp metadata extraction timed out for: " + url);
            }
            if (process.exitValue() != 0) {
                throw new IOException("yt-dlp metadata extraction failed (exit code " + process.exitValue() + ") for: " + url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("yt-dlp metadata extraction interrupted", e);
        }

        if (output == null || output.isBlank()) {
            throw new IOException("yt-dlp returned no metadata for: " + url);
        }

        String[] parts = output.split("\\|\\|\\|", -1);
        VideoMetadata meta = new VideoMetadata();
        meta.setTitle(parts.length > 0 ? parts[0].trim() : null);
        meta.setExtension(parts.length > 1 ? parts[1].trim() : "mp4");
        if (parts.length > 2 && !parts[2].trim().isEmpty() && !"NA".equals(parts[2].trim())) {
            try {
                meta.setDurationSeconds(Long.parseLong(parts[2].trim()));
            } catch (NumberFormatException ignored) {}
        }
        meta.setThumbnailUrl(parts.length > 3 ? parts[3].trim() : null);

        return meta;
    }

    /**
     * Download a video to the specified directory using yt-dlp.
     * Downloads as mp4 format (with ffmpeg merge if needed).
     *
     * @param url           the video URL (YouTube, etc.)
     * @param outputDir     the directory to save the file in
     * @param filename      the desired output filename (without extension), or null to use video ID
     * @param maxResolution max video height in pixels (e.g. 720 for 720p), 0 for unlimited
     * @return the download result containing the file path and metadata
     */
    public DownloadResult download(String url, Path outputDir, String filename, int maxResolution) throws IOException {
        // First extract metadata
        VideoMetadata metadata = extractMetadata(url);

        // Build output template
        String outputTemplate;
        if (filename != null && !filename.isBlank()) {
            // Strip extension if provided — yt-dlp will add the correct one
            String baseName = filename;
            int dot = filename.lastIndexOf('.');
            if (dot > 0) baseName = filename.substring(0, dot);
            outputTemplate = outputDir.resolve(baseName + ".%(ext)s").toString();
        } else {
            outputTemplate = outputDir.resolve("%(id)s.%(ext)s").toString();
        }

        // Build format selector with resolution cap.
        // Select H.264 (avc1) + AAC (mp4a) for maximum macOS/browser compatibility.
        String heightFilter = maxResolution > 0 ? "[height<=" + maxResolution + "]" : "";
        String formatSelector = "bestvideo[vcodec^=avc1]" + heightFilter + "+bestaudio[acodec^=mp4a]"
                + "/bestvideo[ext=mp4]" + heightFilter + "+bestaudio[ext=m4a]"
                + "/best[ext=mp4]" + heightFilter
                + "/best" + heightFilter;

        ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "-f", formatSelector,
                "--merge-output-format", "mp4",
                "--postprocessor-args", "ffmpeg:-c:v libx264 -c:a aac -movflags +faststart",
                "-o", outputTemplate,
                "--no-playlist",
                "--print", "after_move:filepath",
                url
        );
        pb.directory(outputDir.toFile());
        pb.redirectErrorStream(false);

        log.info("Starting yt-dlp download: {}", url);
        Process process = pb.start();

        // Read stdout for the final filepath
        String downloadedPath;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            // Read stderr in background to prevent blocking
            Thread errThread = new Thread(() -> {
                try (BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errReader.readLine()) != null) {
                        log.debug("yt-dlp: {}", line);
                    }
                } catch (IOException ignored) {}
            });
            errThread.setDaemon(true);
            errThread.start();

            // The last line of stdout should be the filepath (from --print after_move:filepath)
            String line;
            downloadedPath = null;
            while ((line = reader.readLine()) != null) {
                downloadedPath = line.trim();
            }
        }

        try {
            boolean finished = process.waitFor(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("yt-dlp download timed out for: " + url);
            }
            if (process.exitValue() != 0) {
                throw new IOException("yt-dlp download failed (exit code " + process.exitValue() + ") for: " + url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("yt-dlp download interrupted", e);
        }

        if (downloadedPath == null || downloadedPath.isBlank()) {
            throw new IOException("yt-dlp did not return a file path for: " + url);
        }

        Path filePath = Path.of(downloadedPath);
        if (!java.nio.file.Files.exists(filePath)) {
            throw new IOException("Downloaded file not found: " + downloadedPath);
        }

        DownloadResult result = new DownloadResult();
        result.setFilePath(filePath);
        result.setFilename(filePath.getFileName().toString());
        metadata.setExtension("mp4"); // we forced mp4 output
        result.setMetadata(metadata);

        log.info("yt-dlp download complete: {} ({} bytes)", result.getFilename(),
                java.nio.file.Files.size(filePath));
        return result;
    }
}
