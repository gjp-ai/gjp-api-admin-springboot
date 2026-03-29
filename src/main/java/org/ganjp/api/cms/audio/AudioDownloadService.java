package org.ganjp.api.cms.audio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.cms.video.YtDlpService;
import org.ganjp.api.common.util.CmsUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Handles audio downloads asynchronously so the API can return immediately.
 * For YouTube URLs, uses yt-dlp to extract audio as mp3 (no video kept).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioDownloadService {

    private final AudioRepository audioRepository;
    private final AudioUploadProperties uploadProperties;
    private final YtDlpService ytDlpService;

    @Async
    @Transactional
    public void downloadInBackground(String audioId, AudioCreateByUrlRequest request) {
        log.info("Starting background audio download for: {}", audioId);

        Audio audio = audioRepository.findById(audioId).orElse(null);
        if (audio == null) {
            log.error("Audio not found for background download: {}", audioId);
            return;
        }

        audio.setDownloadStatus(Audio.DownloadStatus.DOWNLOADING);
        audioRepository.save(audio);

        try {
            String url = request.getOriginalUrl();
            boolean isYouTube = CmsUtil.isYouTubeUrl(url);
            String baseDir = uploadProperties.getDirectory();
            Files.createDirectories(Path.of(baseDir));

            if (isYouTube) {
                downloadFromYouTube(audio, request, baseDir);
            } else {
                downloadDirect(audio, request, baseDir);
            }

            audio.setDownloadStatus(Audio.DownloadStatus.COMPLETED);
            audio.setDownloadError(null);
            audioRepository.save(audio);
            log.info("Background audio download completed: {}", audioId);
        } catch (Exception e) {
            log.error("Background audio download failed: {}", audioId, e);
            audio.setDownloadStatus(Audio.DownloadStatus.FAILED);
            audio.setDownloadError(e.getMessage() != null
                    ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500))
                    : "Unknown error");
            audioRepository.save(audio);
        }
    }

    /**
     * Extract audio from YouTube URL as mp3 using yt-dlp, then download thumbnail as cover.
     */
    private void downloadFromYouTube(Audio audio, AudioCreateByUrlRequest request, String baseDir) throws IOException {
        if (!ytDlpService.isAvailable()) {
            throw new IllegalStateException("yt-dlp is not installed. Install it with: brew install yt-dlp");
        }

        // Extract audio only as mp3
        YtDlpService.DownloadResult result = ytDlpService.downloadAudio(
                request.getOriginalUrl(), Path.of(baseDir), request.getFilename());

        String filename = result.getFilename();

        // Rename if the caller specified a different filename
        if (request.getFilename() != null && !request.getFilename().isBlank()) {
            String desired = request.getFilename();
            if (!desired.contains(".")) desired = desired + ".mp3";
            if (!desired.equals(filename)) {
                Path source = result.getFilePath();
                Path target = CmsUtil.resolveSecurePath(baseDir, desired);
                if (Files.exists(target)) {
                    throw new IllegalArgumentException("Filename already exists: " + desired);
                }
                Files.move(source, target);
                filename = desired;
            }
        }

        if (audioRepository.existsByFilenameAndIdNot(filename, audio.getId())) {
            Files.deleteIfExists(CmsUtil.resolveSecurePath(baseDir, filename));
            throw new IllegalArgumentException("Filename already exists in database: " + filename);
        }

        audio.setFilename(filename);
        audio.setSizeBytes(Files.size(CmsUtil.resolveSecurePath(baseDir, filename)));

        // Download thumbnail as cover image, using audio filename as base
        YtDlpService.VideoMetadata meta = result.getMetadata();
        if (meta.getThumbnailUrl() != null) {
            String coverFilename = request.getCoverImageFilename();
            if (coverFilename == null || coverFilename.isBlank()) {
                int dot = filename.lastIndexOf('.');
                String baseName = dot > 0 ? filename.substring(0, dot) : filename;
                coverFilename = baseName + "-cover.jpg";
            }
            downloadCoverImage(audio, meta.getThumbnailUrl(), coverFilename, baseDir);
        }
    }

    /**
     * Download audio from a direct URL (non-YouTube).
     */
    private void downloadDirect(Audio audio, AudioCreateByUrlRequest request, String baseDir) throws IOException {
        String url = request.getOriginalUrl();
        String filename = request.getFilename();
        if (filename == null || filename.isBlank()) {
            try {
                java.net.URL u = new java.net.URL(url);
                String path = u.getPath();
                int lastSlash = path.lastIndexOf('/');
                String lastSeg = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
                if (lastSeg == null || lastSeg.isBlank()) {
                    lastSeg = System.currentTimeMillis() + "-audio.mp3";
                }
                filename = lastSeg.replaceAll("\\s+", "-");
            } catch (Exception ex) {
                filename = System.currentTimeMillis() + "-audio.mp3";
            }
        }

        Path target = CmsUtil.resolveSecurePath(baseDir, filename);

        if (audioRepository.existsByFilenameAndIdNot(filename, audio.getId())) {
            throw new IllegalArgumentException("Filename already exists: " + filename);
        }

        try (java.io.InputStream is = new java.net.URL(url).openStream()) {
            byte[] data = is.readAllBytes();
            Files.write(target, data, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
            audio.setSizeBytes((long) data.length);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            throw new IllegalArgumentException("File already exists: " + filename);
        }

        audio.setFilename(filename);

        // Handle optional cover image from URL
        if (request.getCoverImageUrl() != null && !request.getCoverImageUrl().isBlank()) {
            downloadCoverImage(audio, request.getCoverImageUrl(), request.getCoverImageFilename(), baseDir);
        }
    }

    private void downloadCoverImage(Audio audio, String coverUrl, String coverFilename, String baseDir) throws IOException {
        String coverDir = baseDir + "/cover-images";
        Files.createDirectories(Path.of(coverDir));

        if (coverFilename == null || coverFilename.isBlank()) {
            try {
                java.net.URL u = new java.net.URL(coverUrl);
                String path = u.getPath();
                int lastSlash = path.lastIndexOf('/');
                String lastSeg = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
                if (lastSeg == null || lastSeg.isBlank()) lastSeg = System.currentTimeMillis() + "-cover.jpg";
                coverFilename = lastSeg.replaceAll("\\s+", "-");
            } catch (Exception ex) {
                coverFilename = System.currentTimeMillis() + "-cover.jpg";
            }
        }

        Path coverTarget = CmsUtil.resolveSecurePath(coverDir, coverFilename);
        if (Files.exists(coverTarget)) {
            throw new IllegalArgumentException("Cover image file already exists: " + coverFilename);
        }

        try (java.io.InputStream is = new java.net.URL(coverUrl).openStream()) {
            byte[] data = is.readAllBytes();
            Files.write(coverTarget, data, java.nio.file.StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            throw new IllegalArgumentException("Cover image file already exists: " + coverFilename);
        }

        resizeCoverImageFile(coverTarget, coverFilename);
        audio.setCoverImageFilename(coverFilename);
    }

    private void resizeCoverImageFile(Path coverTarget, String coverFilename) {
        try {
            BufferedImage coverImg = ImageIO.read(coverTarget.toFile());
            if (coverImg != null) {
                BufferedImage resized = resizeImageIfNeeded(coverImg, uploadProperties.getCoverImage().getMaxSize());
                if (resized != coverImg) {
                    String ext = CmsUtil.getFileExtension(coverFilename);
                    if (ext.isEmpty()) ext = "jpg";
                    ImageIO.write(resized, ext, coverTarget.toFile());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to resize cover image: {}", e.getMessage());
        }
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
