package org.ganjp.api.cms.audio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.ganjp.api.common.config.CmsProperties;
import org.ganjp.api.common.exception.ResourceNotFoundException;
import org.ganjp.api.common.util.CmsUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
public class AudioService {
    private final AudioRepository audioRepository;
    private final AudioUploadProperties uploadProperties;
    private final CmsProperties cmsProperties;
    private final AudioDownloadService audioDownloadService;

    public AudioResponse createAudio(AudioCreateRequest request, String userId) throws IOException {
        Audio audio = new Audio();
        String id = UUID.randomUUID().toString();
        audio.setId(id);
        audio.setName(request.getName());
        audio.setCoverImageFilename(request.getCoverImageFilename());
        if (request.getOriginalUrl() != null) audio.setOriginalUrl(request.getOriginalUrl());
        if (request.getSourceName() != null) audio.setSourceName(request.getSourceName());
        audio.setDescription(request.getDescription());
        if (request.getSubtitle() != null) audio.setSubtitle(request.getSubtitle());
        if (request.getArtist() != null) audio.setArtist(request.getArtist());
        audio.setTags(request.getTags());
        if (request.getLang() != null) audio.setLang(request.getLang());
        if (request.getDisplayOrder() != null) audio.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsActive() != null) audio.setIsActive(request.getIsActive());

        if (request.getFile() != null && !request.getFile().isEmpty()) {
            MultipartFile file = request.getFile();
            String originalFilename = file.getOriginalFilename();
            String filename;
            if (request.getFilename() != null && !request.getFilename().isBlank() && request.getFilename().lastIndexOf(".") > 0) {
                filename = request.getFilename();
            } else if (originalFilename == null || originalFilename.isBlank()) {
                filename = System.currentTimeMillis() + "-audio";
            } else {
                filename = originalFilename.replaceAll("\\s+", "-");
            }
            Path audioDir = Path.of(uploadProperties.getDirectory());
            Files.createDirectories(audioDir);
            Path target = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), filename);

            if (audioRepository.existsByFilename(filename)) {
                throw new IllegalArgumentException("Filename already exists: " + filename);
            }

            try {
                Files.copy(file.getInputStream(), target);
            } catch (java.nio.file.FileAlreadyExistsException e) {
                throw new IllegalArgumentException("Filename already exists: " + filename);
            }
            audio.setFilename(filename);
            audio.setSizeBytes(Files.size(target));
        } else {
            throw new IllegalArgumentException("file is required");
        }

        // cover image
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

            if (Files.exists(coverTarget) || audioRepository.existsByFilename(coverFilename)) {
                throw new IllegalArgumentException("Audio Cover image already exists: " + coverFilename);
            }

            try {
                BufferedImage original = ImageIO.read(cover.getInputStream());
                if (original != null) {
                    String ext = "png";
                    int dot = coverFilename.lastIndexOf('.');
                    if (dot > 0 && dot < coverFilename.length() - 1) ext = coverFilename.substring(dot + 1).toLowerCase();
                    // WebP → PNG/JPG conversion
                    if (CmsUtil.isWebpExtension(ext)) {
                        ext = CmsUtil.resolveWebpOutputFormat(original);
                        original = CmsUtil.prepareForOutput(original, ext);
                        coverFilename = CmsUtil.replaceExtension(coverFilename, ext);
                        coverTarget = CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", coverFilename);
                        log.info("Converted WebP audio cover image to {}: {}", ext.toUpperCase(), coverFilename);
                    }
                    BufferedImage resized = resizeImageIfNeeded(original, uploadProperties.getCoverImage().getMaxSize());
                    ImageIO.write(resized, ext, coverTarget.toFile());
                } else {
                    Files.copy(cover.getInputStream(), coverTarget);
                }
            } catch (IOException e) {
                Files.copy(cover.getInputStream(), coverTarget);
            }
            audio.setCoverImageFilename(coverFilename);
        }

        audio.setCreatedBy(userId);
        audio.setUpdatedBy(userId);

        Audio saved = audioRepository.save(audio);
        return toResponse(saved);
    }

    /**
     * Create an audio by downloading from a URL.
     * For YouTube URLs, extracts audio only as MP3 using yt-dlp.
     * For direct URLs, downloads the file directly.
     * Returns immediately with downloadStatus=PENDING; download runs in background.
     */
    public AudioResponse createAudioByUrl(AudioCreateByUrlRequest request, String userId) {
        String url = request.getOriginalUrl();
        boolean isYouTube = CmsUtil.isYouTubeUrl(url);

        Audio audio = new Audio();
        String id = UUID.randomUUID().toString();
        audio.setId(id);
        audio.setName(request.getName());
        audio.setOriginalUrl(url);
        if (request.getFilename() != null) audio.setFilename(request.getFilename());
        audio.setSourceName(isYouTube ? "youtube" : request.getSourceName());
        audio.setDescription(request.getDescription());
        if (request.getSubtitle() != null) audio.setSubtitle(request.getSubtitle());
        if (request.getArtist() != null) audio.setArtist(request.getArtist());
        audio.setTags(request.getTags());
        if (request.getLang() != null) audio.setLang(request.getLang());
        if (request.getDisplayOrder() != null) audio.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsActive() != null) audio.setIsActive(request.getIsActive());
        audio.setDownloadStatus(Audio.DownloadStatus.PENDING);
        audio.setCreatedBy(userId);
        audio.setUpdatedBy(userId);

        Audio saved = audioRepository.save(audio);
        log.info("Audio record created, queueing background download: {}", saved.getId());

        // Dispatch async download AFTER this transaction commits
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                audioDownloadService.downloadInBackground(saved.getId(), request);
            }
        });

        return toResponse(saved);
    }

    public AudioResponse updateAudio(String id, AudioUpdateRequest request, String userId) {
        Audio audio = audioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audio", "id", id));
        if (request.getName() != null) audio.setName(request.getName());
        if (request.getOriginalUrl() != null) audio.setOriginalUrl(request.getOriginalUrl());
        if (request.getSourceName() != null) audio.setSourceName(request.getSourceName());

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

                int suffix = 1;
                String base = coverFilename;
                String ext = "";
                int dot = coverFilename.lastIndexOf('.');
                if (dot > 0) {
                    base = coverFilename.substring(0, dot);
                    ext = coverFilename.substring(dot);
                }
                while (Files.exists(coverTarget) || audioRepository.existsByFilename(coverFilename)) {
                    coverFilename = base + "-" + suffix + ext;
                    coverTarget = CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", coverFilename);
                    suffix++;
                }

                if (audio.getCoverImageFilename() != null) {
                    try { Path old = CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", audio.getCoverImageFilename()); Files.deleteIfExists(old); } catch (IOException ignored) {}
                }

                try {
                    BufferedImage original = ImageIO.read(cover.getInputStream());
                    if (original != null) {
                        String writeExt = "png";
                        if (dot > 0 && dot < coverFilename.length() - 1) writeExt = coverFilename.substring(dot + 1).toLowerCase();
                        // WebP → PNG/JPG conversion
                        if (CmsUtil.isWebpExtension(writeExt)) {
                            writeExt = CmsUtil.resolveWebpOutputFormat(original);
                            original = CmsUtil.prepareForOutput(original, writeExt);
                            coverFilename = CmsUtil.replaceExtension(coverFilename, writeExt);
                            coverTarget = CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", coverFilename);
                            log.info("Converted WebP audio cover image to {}: {}", writeExt.toUpperCase(), coverFilename);
                        }
                        BufferedImage resized = resizeImageIfNeeded(original, uploadProperties.getCoverImage().getMaxSize());
                        ImageIO.write(resized, writeExt, coverTarget.toFile());
                    } else {
                        Files.copy(cover.getInputStream(), coverTarget, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    Files.copy(cover.getInputStream(), coverTarget, StandardCopyOption.REPLACE_EXISTING);
                }
                audio.setCoverImageFilename(coverFilename);
            }

            // handle cover image filename change only (rename existing file)
            if (request.getCoverImageFilename() != null &&
                    request.getCoverImageFilename().lastIndexOf('.') > 0 &&
                    !request.getCoverImageFilename().equals(audio.getCoverImageFilename())) {
                // change the image file name in local storage only (no re-download), implying a rename
                Path coverImagesDir = Path.of(uploadProperties.getDirectory(), "cover-images");
                Path oldPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", audio.getCoverImageFilename());
                Path newPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", request.getCoverImageFilename());
                // if newPath exists, it will not be overwritten
                if (Files.exists(newPath)) {
                    throw new IllegalArgumentException("Cover image file with name " + request.getCoverImageFilename() + " already exists");
                }
                
                if (Files.exists(oldPath)) {
                    Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                }

                audio.setCoverImageFilename(request.getCoverImageFilename());
            }

            // handle audio filename change (rename existing file)
            if (request.getFilename() != null && 
                    request.getFilename().lastIndexOf('.') > 0 &&
                    !request.getFilename().equals(audio.getFilename())) {
                Path audioDir = Path.of(uploadProperties.getDirectory());
                Path oldPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), audio.getFilename());
                Path newPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), request.getFilename());
                // if newPath exists, it will not be overwritten
                if (Files.exists(newPath)) {
                    throw new IllegalArgumentException("Audio file with name " + request.getFilename() + " already exists");
                }

                if (Files.exists(oldPath)) {
                    Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                }

                audio.setFilename(request.getFilename());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to save cover image: " + e.getMessage());
        }

        if (request.getDescription() != null) audio.setDescription(request.getDescription());
        if (request.getSubtitle() != null) audio.setSubtitle(request.getSubtitle());
        if (request.getArtist() != null) audio.setArtist(request.getArtist());
        if (request.getTags() != null) audio.setTags(request.getTags());
        if (request.getLang() != null) audio.setLang(request.getLang());
        if (request.getDisplayOrder() != null) audio.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsActive() != null) audio.setIsActive(request.getIsActive());

        audio.setUpdatedBy(userId);
        Audio saved = audioRepository.save(audio);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AudioResponse getAudioById(String id) {
        Audio audio = audioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audio", "id", id));
        return toResponse(audio);
    }

    @Transactional(readOnly = true)
    public List<AudioResponse> listAudios() {
        List<Audio> all = audioRepository.findAll();
        return all.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public java.io.File getAudioFileByFilename(String filename) {
        if (filename == null) throw new IllegalArgumentException("filename is null");
        Path audioPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), filename);
        if (!Files.exists(audioPath)) {
            throw new ResourceNotFoundException("Audio file", "filename", filename);
        }
        return audioPath.toFile();
    }

    @Transactional(readOnly = true)
    public java.io.File getCoverImageFileByFilename(String filename) {
        if (filename == null) throw new IllegalArgumentException("filename is null");
        Path coverPath = CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", filename);
        if (!Files.exists(coverPath)) {
            throw new ResourceNotFoundException("Audio cover image", "filename", filename);
        }
        return coverPath.toFile();
    }

    public void deleteAudio(String id, String userId) {
        Audio audio = audioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audio", "id", id));
        audio.setIsActive(false);
        audio.setUpdatedBy(userId);
        audioRepository.save(audio);
        log.info("Audio soft deleted: {} by user: {}", id, userId);
    }

    public void permanentlyDeleteAudio(String id) {
        Audio audio = audioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audio", "id", id));
        String filename = audio.getFilename();
        String coverFilename = audio.getCoverImageFilename();
        audioRepository.delete(audio);
        if (filename != null) {
            CmsUtil.moveToDeletedFolder(CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), filename));
        }
        if (coverFilename != null) {
            CmsUtil.moveToDeletedFolder(CmsUtil.resolveSecurePath(uploadProperties.getDirectory() + "/cover-images", coverFilename));
        }
        log.info("Audio permanently deleted: {}", id);
    }

    @Transactional(readOnly = true)
    public Page<AudioResponse> searchAudios(String name, Audio.Language lang, String tags, Boolean isActive, Pageable pageable) {
        Page<Audio> page = audioRepository.searchAudios(name, lang, tags, isActive, pageable);
        return page.map(this::toResponse);
    }

    private AudioResponse toResponse(Audio a) {
        return AudioResponse.from(a, cmsProperties.getBaseUrl());
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
