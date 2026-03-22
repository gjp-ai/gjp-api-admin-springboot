package org.ganjp.api.cms.logo;
import java.io.File;
import java.nio.file.Paths;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.cms.logo.LogoCreateRequest;
import org.ganjp.api.cms.logo.LogoResponse;
import org.ganjp.api.cms.logo.LogoUpdateRequest;
import org.ganjp.api.cms.logo.Logo;
import org.ganjp.api.cms.logo.LogoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing logos
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogoService {
    private final LogoRepository logoRepository;
    private final LogoProcessingService logoProcessingService;

    /**
     * Flexible search for logos by name, language, tags, and status
     */
    public List<LogoResponse> searchLogos(String name, Logo.Language lang, String tags, Boolean isActive) {
        return logoRepository.searchLogos(name, lang, tags, isActive)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Flexible search for logos by name, language, tags, and status with pagination
     */
    public Page<LogoResponse> searchLogos(String name, Logo.Language lang, String tags, Boolean isActive, Pageable pageable) {
        return logoRepository.searchLogos(name, lang, tags, isActive, pageable)
                .map(this::toResponse);
    }

    /**
     * Create a new logo
     */
    @Transactional
    public LogoResponse createLogo(LogoCreateRequest request, String userId) throws IOException {
        log.info("Creating new logo: {}", request.getName());

        // Validate image source
        if (!request.hasImageSource()) {
            throw new IllegalArgumentException("Either file upload or original URL must be provided");
        }

        // Process image (upload or download from URL)
        LogoProcessingService.ProcessedImage processedImage;
        if (request.getFile() != null && !request.getFile().isEmpty()) {
            processedImage = logoProcessingService.processUploadedFile(request.getFile(), request.getName());
        } else {
            processedImage = logoProcessingService.processImageFromUrl(request.getOriginalUrl(), request.getName());
        }

        // Create logo entity
        Logo logo = Logo.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName())
                .originalUrl(request.getOriginalUrl())
                .filename(processedImage.getFilename())
                .extension(processedImage.getExtension())
                .tags(request.getTags())
                .lang(request.getLang())
                .displayOrder(request.getDisplayOrder())
                .isActive(request.getIsActive())
                .build();

        logo.setCreatedBy(userId);
        logo.setUpdatedBy(userId);

        Logo savedLogo = logoRepository.save(logo);
        log.info("Logo created successfully with ID: {}", savedLogo.getId());

        return toResponse(savedLogo);
    }

    /**
     * Update an existing logo
     */
    @Transactional
    public LogoResponse updateLogo(String id, LogoUpdateRequest request, String userId) throws IOException {
        log.info("Updating logo: {}", id);

        Logo logo = logoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Logo not found with ID: " + id));

        String oldFilename = logo.getFilename();
        boolean imageUpdated = false;
        boolean nameChanged = false;
        boolean extensionChanged = false;

        // Check if name is being changed
        if (request.getName() != null && !request.getName().equals(logo.getName())) {
            nameChanged = true;
        }

        // Check if extension is being changed
        if (request.getExtension() != null && !request.getExtension().equals(logo.getExtension())) {
            extensionChanged = true;
        }

        if ((nameChanged || extensionChanged) && !imageUpdated) {
                // If extension changed, convert image format
                String nameForFilename = request.getName() != null ? request.getName() : logo.getName();
                String extensionForFilename = request.getExtension() != null ? request.getExtension() : logo.getExtension();
                File oldFile = Paths.get(logoProcessingService.getUploadProperties().getDirectory()).resolve(oldFilename).toFile();
                String newFilename;
                if (extensionChanged) {
                    newFilename = logoProcessingService.convertImageFormat(oldFile, extensionForFilename, nameForFilename);
                } else {
                    newFilename = logoProcessingService.renameLogoFile(oldFilename, nameForFilename, extensionForFilename);
                }
                if (newFilename != null) {
                    logo.setFilename(newFilename);
                    if (extensionChanged) {
                        logo.setExtension(request.getExtension());
                        // Optionally delete the old file if conversion succeeded
                        if (!oldFilename.equals(newFilename) && oldFile.exists()) {
                            oldFile.delete();
                        }
                    }
                }
        }

        // Update other fields if provided
        if (request.getName() != null) {
            logo.setName(request.getName());
        }
        if (request.getTags() != null) {
            logo.setTags(request.getTags());
        }
        if (request.getLang() != null) {
            logo.setLang(request.getLang());
        }
        if (request.getDisplayOrder() != null) {
            logo.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getIsActive() != null) {
            logo.setIsActive(request.getIsActive());
        }

        logo.setUpdatedBy(userId);

        Logo updatedLogo = logoRepository.save(logo);
        
        // Delete old image file if image was updated (replaced with new image)
        if (imageUpdated && oldFilename != null) {
            logoProcessingService.deleteLogoFile(oldFilename);
        }

        log.info("Logo updated successfully: {}", id);
        return toResponse(updatedLogo);
    }

    /**
     * Get logo by ID
     */
    public LogoResponse getLogoById(String id) {
        Logo logo = logoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Logo not found with ID: " + id));
        return toResponse(logo);
    }

    /**
     * Get logo file by filename for viewing in browser
     * @param filename The filename to retrieve
     * @return File object representing the logo file
     * @throws IOException if file not found or error reading file
     */
    public java.io.File getLogoFileByFilename(String filename) throws IOException {
        // Validate that the filename exists in database for security
        List<Logo> logos = logoRepository.findAll();
        boolean filenameExists = logos.stream()
                .anyMatch(logo -> filename.equals(logo.getFilename()));
        
        if (!filenameExists) {
            throw new IllegalArgumentException("Logo not found with filename: " + filename);
        }
        
        return logoProcessingService.getLogoFile(filename);
    }

    /**
     * Get all active logos
     */
    public List<LogoResponse> getAllActiveLogos() {
        return logoRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all logos (including inactive)
     */
    public List<LogoResponse> getAllLogos() {
        return logoRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Search logos by name
     */
    public List<LogoResponse> searchLogosByName(String keyword) {
        return logoRepository.searchByNameContaining(keyword)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Find logos by tag
     */
    public List<LogoResponse> findLogosByTag(String tag) {
        return logoRepository.findByTagsContaining(tag)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Soft delete logo
     */
    @Transactional
    public void deleteLogo(String id, String userId) {
        Logo logo = logoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Logo not found with ID: " + id));

        logo.setIsActive(false);
        logo.setUpdatedBy(userId);
        logoRepository.save(logo);

        log.info("Logo soft deleted: {}", id);
    }

    /**
     * Permanently delete logo (hard delete)
     */
    @Transactional
    public void permanentlyDeleteLogo(String id) {
        Logo logo = logoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Logo not found with ID: " + id));

        String filename = logo.getFilename();
        
        logoRepository.delete(logo);
        
        // Delete physical file
        if (filename != null) {
            logoProcessingService.deleteLogoFile(filename);
        }

        log.info("Logo permanently deleted: {}", id);
    }

    /**
     * Convert Logo entity to LogoResponse DTO
     */
    private LogoResponse toResponse(Logo logo) {
        return LogoResponse.builder()
                .id(logo.getId())
                .name(logo.getName())
                .originalUrl(logo.getOriginalUrl())
                .filename(logo.getFilename())
                .extension(logo.getExtension())
                .tags(logo.getTags())
                .lang(logo.getLang())
                .displayOrder(logo.getDisplayOrder())
                .isActive(logo.getIsActive())
                .createdAt(logo.getCreatedAt())
                .updatedAt(logo.getUpdatedAt())
                .createdBy(logo.getCreatedBy())
                .updatedBy(logo.getUpdatedBy())
                .build();
    }
}
