package org.ganjp.api.cms.website;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.cms.website.CreateWebsiteRequest;
import org.ganjp.api.cms.website.UpdateWebsiteRequest;
import org.ganjp.api.cms.website.WebsiteResponse;
import org.ganjp.api.cms.website.Website;
import org.ganjp.api.cms.website.WebsiteRepository;
import org.ganjp.api.common.exception.ResourceNotFoundException;
import org.ganjp.api.common.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for Website management
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WebsiteService {

    private final WebsiteRepository websiteRepository;

    /**
     * Create a new website
     */
    public WebsiteResponse createWebsite(CreateWebsiteRequest request, String createdBy) {
        log.info("Creating new website: {} by user: {}", request.getName(), createdBy);

        // Validate unique name per language
        if (websiteRepository.existsByNameAndLang(request.getName(), request.getLang())) {
            throw new BusinessException(String.format("Website with name '%s' already exists for language '%s'", 
                request.getName(), request.getLang()));
        }

        Website website = Website.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName())
                .url(request.getUrl())
                .logoUrl(request.getLogoUrl())
                .description(request.getDescription())
                .tags(request.getTags())
                .lang(request.getLang())
                .displayOrder(request.getDisplayOrder())
                .isActive(request.getIsActive())
                .build();

        // Set audit fields
        website.setCreatedBy(createdBy);
        website.setUpdatedBy(createdBy);

        Website savedWebsite = websiteRepository.save(website);
        log.info("Website created successfully with ID: {}", savedWebsite.getId());

        return WebsiteResponse.from(savedWebsite);
    }

    /**
     * Update an existing website
     */
    public WebsiteResponse updateWebsite(String id, UpdateWebsiteRequest request, String updatedBy) {
        log.info("Updating website with ID: {} by user: {}", id, updatedBy);

        Website website = websiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Website not found with ID: " + id));

        // Validate unique name per language if name is being updated
        if (request.getName() != null && !request.getName().equals(website.getName())) {
            Website.Language langToCheck = request.getLang() != null ? request.getLang() : website.getLang();
            if (websiteRepository.existsByNameAndLangExcludingId(request.getName(), langToCheck, id)) {
                throw new BusinessException(String.format("Website with name '%s' already exists for language '%s'", 
                    request.getName(), langToCheck));
            }
        }

        // Update fields if provided
        if (request.getName() != null) {
            website.setName(request.getName());
        }
        if (request.getUrl() != null) {
            website.setUrl(request.getUrl());
        }
        if (request.getLogoUrl() != null) {
            website.setLogoUrl(request.getLogoUrl());
        }
        if (request.getDescription() != null) {
            website.setDescription(request.getDescription());
        }
        if (request.getTags() != null) {
            website.setTags(request.getTags());
        }
        if (request.getLang() != null) {
            website.setLang(request.getLang());
        }
        if (request.getDisplayOrder() != null) {
            website.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getIsActive() != null) {
            website.setIsActive(request.getIsActive());
        }

        // Update audit fields
        website.setUpdatedBy(updatedBy);

        Website updatedWebsite = websiteRepository.save(website);
        log.info("Website updated successfully: {}", updatedWebsite.getId());

        return WebsiteResponse.from(updatedWebsite);
    }

    /**
     * Get website by ID
     */
    @Transactional(readOnly = true)
    public WebsiteResponse getWebsiteById(String id) {
        log.debug("Retrieving website with ID: {}", id);

        Website website = websiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Website not found with ID: " + id));

        return WebsiteResponse.from(website);
    }

    /**
     * Get all websites with pagination and filtering
     */
    @Transactional(readOnly = true)
    /**
     * Flexible search for websites by name, language, tags, and status
     */
    public Page<WebsiteResponse> getWebsites(String name, Website.Language lang, String tags, Boolean isActive, Pageable pageable) {
        log.debug("Retrieving websites with filters - name: {}, lang: {}, tags: {}, isActive: {}", name, lang, tags, isActive);
        Page<Website> websites = websiteRepository.searchWebsites(name, lang, tags, isActive, pageable);
        return websites.map(WebsiteResponse::from);
    }

    /**
     * Get websites by language
     */
    @Transactional(readOnly = true)
    public List<WebsiteResponse> getWebsitesByLanguage(Website.Language lang, boolean activeOnly) {
        log.debug("Retrieving websites for language: {}, activeOnly: {}", lang, activeOnly);

        List<Website> websites = activeOnly 
            ? websiteRepository.findByLangAndIsActiveTrueOrderByDisplayOrderAsc(lang)
            : websiteRepository.findByLangOrderByDisplayOrderAsc(lang);

        return websites.stream()
                .map(WebsiteResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Get websites by tag
     */
    @Transactional(readOnly = true)
    public List<WebsiteResponse> getWebsitesByTag(String tag, boolean activeOnly) {
        log.debug("Retrieving websites with tag: {}, activeOnly: {}", tag, activeOnly);

        List<Website> websites = activeOnly 
            ? websiteRepository.findActiveByTagsContaining(tag)
            : websiteRepository.findByTagsContaining(tag);

        return websites.stream()
                .map(WebsiteResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Get top websites
     */
    @Transactional(readOnly = true)
    public List<WebsiteResponse> getTopWebsites(int limit) {
        log.debug("Retrieving top {} websites", limit);

        List<Website> websites = websiteRepository.findTopActiveWebsites(PageRequest.of(0, limit));
        return websites.stream()
                .map(WebsiteResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Delete website by ID
     */
    public void deleteWebsite(String id) {
        log.info("Deleting website with ID: {}", id);

        Website website = websiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Website not found with ID: " + id));

        websiteRepository.delete(website);
        log.info("Website deleted successfully: {}", id);
    }

    /**
     * Soft delete website (set isActive = false)
     */
    public WebsiteResponse deactivateWebsite(String id, String updatedBy) {
        log.info("Deactivating website with ID: {} by user: {}", id, updatedBy);

        Website website = websiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Website not found with ID: " + id));

        website.setIsActive(false);
        website.setUpdatedBy(updatedBy);

        Website updatedWebsite = websiteRepository.save(website);
        log.info("Website deactivated successfully: {}", id);

        return WebsiteResponse.from(updatedWebsite);
    }

    /**
     * Activate website
     */
    public WebsiteResponse activateWebsite(String id, String updatedBy) {
        log.info("Activating website with ID: {} by user: {}", id, updatedBy);

        Website website = websiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Website not found with ID: " + id));

        website.setIsActive(true);
        website.setUpdatedBy(updatedBy);

        Website updatedWebsite = websiteRepository.save(website);
        log.info("Website activated successfully: {}", id);

        return WebsiteResponse.from(updatedWebsite);
    }

    /**
     * Get website statistics
     */
    @Transactional(readOnly = true)
    public WebsiteStatistics getStatistics() {
        long totalWebsites = websiteRepository.count();
        long activeWebsites = websiteRepository.countByLangAndIsActiveTrue(Website.Language.EN) + 
                             websiteRepository.countByLangAndIsActiveTrue(Website.Language.ZH);
        long englishWebsites = websiteRepository.countByLang(Website.Language.EN);
        long chineseWebsites = websiteRepository.countByLang(Website.Language.ZH);

        return WebsiteStatistics.builder()
                .totalWebsites(totalWebsites)
                .activeWebsites(activeWebsites)
                .englishWebsites(englishWebsites)
                .chineseWebsites(chineseWebsites)
                .build();
    }



    /**
     * Website statistics DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WebsiteStatistics {
        private long totalWebsites;
        private long activeWebsites;
        private long englishWebsites;
        private long chineseWebsites;
    }
}