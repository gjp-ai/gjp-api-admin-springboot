package org.ganjp.api.cms.video;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Handles video downloads asynchronously so the API can return immediately.
 * Separated from VideoService to allow Spring's @Async proxy to work
 * (self-invocation of @Async methods within the same bean is not proxied).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoDownloadService {

    private final VideoRepository videoRepository;
    private final VideoUploadProperties uploadProperties;
    private final YtDlpService ytDlpService;

    /**
     * Download a video from URL in the background.
     * Updates the Video record with filename, sizeBytes, coverImageFilename, and downloadStatus.
     */
    @Async
    @Transactional
    public void downloadInBackground(String videoId, VideoCreateByUrlRequest request) {
        log.info("Starting background download for video: {}", videoId);

        Video video = videoRepository.findById(videoId).orElse(null);
        if (video == null) {
            log.error("Video not found for background download: {}", videoId);
            return;
        }

        video.setDownloadStatus(Video.DownloadStatus.DOWNLOADING);
        videoRepository.save(video);

        try {
            String url = request.getOriginalUrl();
            boolean isYouTube = CmsUtil.isYouTubeUrl(url);
            String baseDir = uploadProperties.getDirectory();
            Path videoDir = Path.of(baseDir);
            Files.createDirectories(videoDir);

            if (isYouTube) {
                downloadYouTubeVideo(video, request, baseDir);
            } else {
                downloadDirectVideo(video, request, baseDir);
            }

            video.setDownloadStatus(Video.DownloadStatus.COMPLETED);
            video.setDownloadError(null);
            videoRepository.save(video);
            log.info("Background download completed for video: {}", videoId);
        } catch (Exception e) {
            log.error("Background download failed for video: {}", videoId, e);
            video.setDownloadStatus(Video.DownloadStatus.FAILED);
            video.setDownloadError(e.getMessage() != null
                    ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500))
                    : "Unknown error");
            videoRepository.save(video);
        }
    }

    private void downloadYouTubeVideo(Video video, VideoCreateByUrlRequest request, String baseDir) throws IOException {
        if (!ytDlpService.isAvailable()) {
            throw new IllegalStateException("yt-dlp is not installed. Install it with: brew install yt-dlp");
        }

        int maxResolution = uploadProperties.getDownload().getMaxResolution();
        YtDlpService.DownloadResult result = ytDlpService.download(
                request.getOriginalUrl(), Path.of(baseDir), request.getFilename(), maxResolution);

        String filename = result.getFilename();

        // Rename if the caller specified a different filename
        if (request.getFilename() != null && !request.getFilename().isBlank()) {
            String desired = request.getFilename();
            if (!desired.contains(".")) desired = desired + ".mp4";
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

        if (videoRepository.existsByFilenameAndIdNot(filename, video.getId())) {
            Files.deleteIfExists(result.getFilePath());
            throw new IllegalArgumentException("Filename already exists in database: " + filename);
        }

        video.setFilename(filename);
        video.setSizeBytes(Files.size(CmsUtil.resolveSecurePath(baseDir, filename)));

        // Download thumbnail as cover image, using video filename as base
        YtDlpService.VideoMetadata meta = result.getMetadata();
        if (meta.getThumbnailUrl() != null) {
            String coverFilename = request.getCoverImageFilename();
            if (coverFilename == null || coverFilename.isBlank()) {
                int dot = filename.lastIndexOf('.');
                String baseName = dot > 0 ? filename.substring(0, dot) : filename;
                coverFilename = baseName + "-cover.jpg";
            }
            downloadCoverImage(video, meta.getThumbnailUrl(), coverFilename, baseDir);
        }
    }

    private void downloadDirectVideo(Video video, VideoCreateByUrlRequest request, String baseDir) throws IOException {
        String url = request.getOriginalUrl();
        String filename = request.getFilename();
        if (filename == null || filename.isBlank()) {
            try {
                java.net.URL u = new java.net.URL(url);
                String path = u.getPath();
                int lastSlash = path.lastIndexOf('/');
                String lastSeg = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
                if (lastSeg == null || lastSeg.isBlank()) {
                    lastSeg = System.currentTimeMillis() + "-video";
                }
                filename = lastSeg.replaceAll("\\s+", "-");
            } catch (Exception ex) {
                filename = System.currentTimeMillis() + "-video";
            }
        }

        Path target = CmsUtil.resolveSecurePath(baseDir, filename);

        if (videoRepository.existsByFilename(filename)) {
            throw new IllegalArgumentException("Filename already exists: " + filename);
        }

        try (java.io.InputStream is = new java.net.URL(url).openStream()) {
            byte[] data = is.readAllBytes();
            Files.write(target, data, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
            video.setSizeBytes((long) data.length);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            throw new IllegalArgumentException("File already exists: " + filename);
        }

        video.setFilename(filename);

        if (request.getCoverImageUrl() != null && !request.getCoverImageUrl().isBlank()) {
            downloadCoverImage(video, request.getCoverImageUrl(), request.getCoverImageFilename(), baseDir);
        }
    }

    private void downloadCoverImage(Video video, String coverUrl, String coverFilename, String baseDir) throws IOException {
        String coverDir = baseDir + "/cover-images";
        Path coverImagesDir = Path.of(coverDir);
        Files.createDirectories(coverImagesDir);

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
        video.setCoverImageFilename(coverFilename);
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
