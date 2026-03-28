package org.ganjp.api.cms.video;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.ganjp.api.common.exception.ResourceNotFoundException;
import org.ganjp.api.common.util.CmsUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
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
public class VideoService {
    private final VideoRepository videoRepository;
    private final VideoUploadProperties uploadProperties;

    public VideoResponse createVideo(VideoCreateRequest request, String userId) throws IOException {
        Video video = new Video();
        String id = UUID.randomUUID().toString();
        video.setId(id);
        video.setName(request.getName());
        // originalUrl, sourceName, width, height, duration removed
        video.setCoverImageFilename(request.getCoverImageFilename());
        // set original source info if provided
        if (request.getOriginalUrl() != null) video.setOriginalUrl(request.getOriginalUrl());
        if (request.getSourceName() != null) video.setSourceName(request.getSourceName());
        video.setDescription(request.getDescription());
        video.setTags(request.getTags());
        if (request.getLang() != null) video.setLang(request.getLang());
        if (request.getDisplayOrder() != null) video.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsActive() != null) video.setIsActive(request.getIsActive());

        // handle file upload (required)
        if (request.getFile() != null && !request.getFile().isEmpty()) {
            MultipartFile file = request.getFile();
            String originalFilename = file.getOriginalFilename();
            // prefer original filename; if missing, fall back to timestamp-based name
            String filename;
            if (request.getFilename() != null && !request.getFilename().isBlank()) {
                filename = request.getFilename();
            } else if (originalFilename == null || originalFilename.isBlank()) {
                filename = System.currentTimeMillis() + "-video";
            } else {
                filename = originalFilename.replaceAll("\\s+", "-");
            }
            Path videoDir = Path.of(uploadProperties.getDirectory());
            Files.createDirectories(videoDir);
            Path target = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), filename);

            // If filename already exists in DB, reject to avoid overwrite
            if (videoRepository.existsByFilename(filename)) {
                throw new IllegalArgumentException("Filename already exists: " + filename);
            }

            try {
                Files.copy(file.getInputStream(), target);
            } catch (java.nio.file.FileAlreadyExistsException e) {
                throw new IllegalArgumentException("Filename already exists: " + filename);
            }
            video.setFilename(filename);
            video.setSizeBytes(Files.size(target));
        } else {
            throw new IllegalArgumentException("file is required");
        }

        // handle optional cover image upload
        if (request.getCoverImageFile() != null && !request.getCoverImageFile().isEmpty()) {
            MultipartFile cover = request.getCoverImageFile();
            String coverOriginal = cover.getOriginalFilename();
            String coverFilename;
            if (request.getCoverImageFilename() != null && !request.getCoverImageFilename().isBlank() && request.getCoverImageFilename().lastIndexOf(".") > 0) {
                coverFilename = request.getCoverImageFilename();
            } else if (coverOriginal == null || coverOriginal.isBlank()) {
                coverFilename = System.currentTimeMillis() + "-cover";
            } else {
                coverFilename = coverOriginal.replaceAll("\\s+", "-");
            }
            Path imagesDir = Path.of(uploadProperties.getDirectory(), "cover-images");
            Files.createDirectories(imagesDir);
            Path coverTarget = CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", coverFilename);

            // if filename exists, auto-rename by appending a numeric suffix
            if (Files.exists(coverTarget) || videoRepository.existsByFilename(coverFilename)) {
                throw new IllegalArgumentException("Video Cover image already exists: " + coverFilename);
            }

            // attempt to read and resize image; if not readable (e.g., SVG), fallback to raw copy
            try {
                BufferedImage original = ImageIO.read(cover.getInputStream());
                if (original != null) {
                    BufferedImage resized = resizeImageIfNeeded(original, uploadProperties.getCoverImage().getMaxSize());
                    String ext = "png";
                    int dot = coverFilename.lastIndexOf('.');
                    if (dot > 0 && dot < coverFilename.length() - 1) ext = coverFilename.substring(dot + 1).toLowerCase();
                    ImageIO.write(resized, ext, coverTarget.toFile());
                } else {
                    // unknown format (SVG etc.), copy raw bytes
                    Files.copy(cover.getInputStream(), coverTarget);
                }
            } catch (IOException e) {
                // if any problem with ImageIO, fallback to raw copy
                Files.copy(cover.getInputStream(), coverTarget);
            }
            video.setCoverImageFilename(coverFilename);
        }

        video.setCreatedBy(userId);
        video.setUpdatedBy(userId);

        Video saved = videoRepository.save(video);
        return toResponse(saved);
    }

    public VideoResponse updateVideo(String id, VideoUpdateRequest request, String userId) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video", "id", id));
        if (request.getName() != null) video.setName(request.getName());
        // originalUrl and sourceName removed
        if (request.getOriginalUrl() != null) video.setOriginalUrl(request.getOriginalUrl());
        if (request.getSourceName() != null) video.setSourceName(request.getSourceName());

        // handle uploaded cover image replacement
        try {
            if (request.getCoverImageFile() != null && !request.getCoverImageFile().isEmpty()) {
                MultipartFile cover = request.getCoverImageFile();
                String coverOriginal = cover.getOriginalFilename();
                String coverFilename;
                if (coverOriginal == null || coverOriginal.isBlank()) {
                    coverFilename = System.currentTimeMillis() + "-cover";
                } else {
                    coverFilename = coverOriginal.replaceAll("\\s+", "-");
                }
                Path imagesDir = Path.of(uploadProperties.getDirectory(), "cover-images");
                Files.createDirectories(imagesDir);
                Path coverTarget = CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", coverFilename);

                // If filename exists, auto-rename by appending numeric suffix
                int suffix = 1;
                String base = coverFilename;
                String ext = "";
                int dot = coverFilename.lastIndexOf('.');
                if (dot > 0) {
                    base = coverFilename.substring(0, dot);
                    ext = coverFilename.substring(dot);
                }
                while (Files.exists(coverTarget) || videoRepository.existsByFilename(coverFilename)) {
                    coverFilename = base + "-" + suffix + ext;
                    coverTarget = CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", coverFilename);
                    suffix++;
                }

                // delete old cover file if present
                if (video.getCoverImageFilename() != null) {
                    try {
                        Path old = CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", video.getCoverImageFilename());
                        Files.deleteIfExists(old);
                    } catch (IOException ignored) {}
                }

                // resize like in create flow
                try {
                    BufferedImage original = ImageIO.read(cover.getInputStream());
                    if (original != null) {
                        BufferedImage resized = resizeImageIfNeeded(original, uploadProperties.getCoverImage().getMaxSize());
                        String writeExt = "png";
                        if (dot > 0 && dot < coverFilename.length() - 1) writeExt = coverFilename.substring(dot + 1).toLowerCase();
                        ImageIO.write(resized, writeExt, coverTarget.toFile());
                    } else {
                        Files.copy(cover.getInputStream(), coverTarget, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    Files.copy(cover.getInputStream(), coverTarget, StandardCopyOption.REPLACE_EXISTING);
                }
                video.setCoverImageFilename(coverFilename);
            }

            // handle cover image filename change only (rename existing file)
            if (request.getCoverImageFilename() != null &&
                    request.getCoverImageFilename().lastIndexOf('.') > 0 &&
                    !request.getCoverImageFilename().equals(video.getCoverImageFilename())) {
                // change the image file name in local storage only (no re-download), implying a rename
                Path oldPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", video.getCoverImageFilename());
                Path newPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", request.getCoverImageFilename());
                // if newPath exists, it will not be overwritten
                if (Files.exists(newPath)) {
                    throw new IllegalArgumentException("Cover image file with name " + request.getCoverImageFilename() + " already exists");
                }
                
                if (Files.exists(oldPath)) {
                    Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                }

                video.setCoverImageFilename(request.getCoverImageFilename());
            }

            // handle video filename change (rename existing file)
            if (request.getFilename() != null && 
                    request.getFilename().lastIndexOf('.') > 0 &&
                    !request.getFilename().equals(video.getFilename())) {
                Path oldPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), video.getFilename());
                Path newPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), request.getFilename());
                // if newPath exists, it will not be overwritten
                if (Files.exists(newPath)) {
                    throw new IllegalArgumentException("Audio file with name " + request.getFilename() + " already exists");
                }

                if (Files.exists(oldPath)) {
                    Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                }

                video.setFilename(request.getFilename());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to save cover image: " + e.getMessage());
        }
    // width/height/duration fields removed
        if (request.getDescription() != null) video.setDescription(request.getDescription());
        if (request.getTags() != null) video.setTags(request.getTags());
        if (request.getLang() != null) video.setLang(request.getLang());
        if (request.getDisplayOrder() != null) video.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsActive() != null) video.setIsActive(request.getIsActive());

        video.setUpdatedBy(userId);
        Video saved = videoRepository.save(video);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public VideoResponse getVideoById(String id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video", "id", id));
        return toResponse(video);
    }

    @Transactional(readOnly = true)
    public List<VideoResponse> listVideos() {
    List<Video> all = videoRepository.findAll();
    return all.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public java.io.File getVideoFileByFilename(String filename) {
        if (filename == null) throw new IllegalArgumentException("filename is null");
        Path videoPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), filename);
        if (!Files.exists(videoPath)) {
            throw new ResourceNotFoundException("Video file", "filename", filename);
        }
        return videoPath.toFile();
    }

    @Transactional(readOnly = true)
    public org.springframework.core.io.Resource getVideoResource(String filename) throws java.io.IOException {
        if (filename == null) throw new IllegalArgumentException("filename is null");
        Path videoPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), filename);
        if (!Files.exists(videoPath)) {
            throw new ResourceNotFoundException("Video file", "filename", filename);
        }
        java.net.URI uri = videoPath.toUri();
        return new org.springframework.core.io.UrlResource(uri);
    }

    @Transactional(readOnly = true)
    public java.io.File getCoverImageFileByFilename(String filename) {
        if (filename == null) throw new IllegalArgumentException("filename is null");
        Path coverPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", filename);
        if (!Files.exists(coverPath)) {
            throw new ResourceNotFoundException("Video cover image", "filename", filename);
        }
        return coverPath.toFile();
    }

    public void deleteVideo(String id, String userId) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video", "id", id));
        video.setIsActive(false);
        video.setUpdatedBy(userId);
        videoRepository.save(video);
        log.info("Video soft deleted: {} by user: {}", id, userId);
    }

    public void permanentlyDeleteVideo(String id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video", "id", id));
        String filename = video.getFilename();
        String coverFilename = video.getCoverImageFilename();
        videoRepository.delete(video);
        try {
            if (filename != null) Files.deleteIfExists(CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), filename));
            if (coverFilename != null) Files.deleteIfExists(CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", coverFilename));
        } catch (IOException e) {
            log.error("Failed to delete video files for video: {}", id, e);
        }
        log.info("Video permanently deleted: {}", id);
    }

    @Transactional(readOnly = true)
    public List<VideoResponse> searchVideos(String name, Video.Language lang, String tags, Boolean isActive) {
    List<Video> list = videoRepository.searchVideos(name, lang, tags, isActive);
    return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<VideoResponse> searchVideos(String name, Video.Language lang, String tags, Boolean isActive, Pageable pageable) {
        return videoRepository.searchVideos(name, lang, tags, isActive, pageable).map(this::toResponse);
    }

    private VideoResponse toResponse(Video v) {
        return VideoResponse.from(v);
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
