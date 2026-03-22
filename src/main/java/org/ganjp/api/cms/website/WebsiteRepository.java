package org.ganjp.api.cms.website;

import org.ganjp.api.cms.website.Website;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Website management
 */
@Repository
public interface WebsiteRepository extends JpaRepository<Website, String> {

    /**
     * Find website by name and language
     */
    Optional<Website> findByNameAndLang(String name, Website.Language lang);

    /**
     * Find websites by language
     */
    List<Website> findByLangOrderByDisplayOrderAsc(Website.Language lang);

    /**
     * Find active websites by language
     */
    List<Website> findByLangAndIsActiveTrueOrderByDisplayOrderAsc(Website.Language lang);

    /**
     * Find websites by tags containing keyword
     */
    @Query("SELECT w FROM Website w WHERE w.tags LIKE %:tag% ORDER BY w.displayOrder ASC")
    List<Website> findByTagsContaining(@Param("tag") String tag);

    /**
     * Find active websites by tags containing keyword
     */
    @Query("SELECT w FROM Website w WHERE w.tags LIKE %:tag% AND w.isActive = true ORDER BY w.displayOrder ASC")
    List<Website> findActiveByTagsContaining(@Param("tag") String tag);

    /**
     * Find websites with search functionality
     */
    /**
     * Flexible search by name, language, tags, and status
     */
    @Query("SELECT w FROM Website w WHERE " +
           "(:name IS NULL OR LOWER(w.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:lang IS NULL OR w.lang = :lang) AND " +
           "(:tags IS NULL OR w.tags LIKE CONCAT('%', :tags, '%')) AND " +
           "(:isActive IS NULL OR w.isActive = :isActive)")
    Page<Website> searchWebsites(
        @Param("name") String name,
        @Param("lang") Website.Language lang,
        @Param("tags") String tags,
        @Param("isActive") Boolean isActive,
        Pageable pageable
    );

    /**
     * Find websites by language with pagination
     */
    Page<Website> findByLang(Website.Language lang, Pageable pageable);

    /**
     * Find active websites by language with pagination
     */
    Page<Website> findByLangAndIsActiveTrue(Website.Language lang, Pageable pageable);

    /**
     * Count websites by language
     */
    long countByLang(Website.Language lang);

    /**
     * Count active websites by language
     */
    long countByLangAndIsActiveTrue(Website.Language lang);

    /**
     * Check if website name exists for a language (excluding specific ID for updates)
     */
    @Query("SELECT COUNT(w) > 0 FROM Website w WHERE w.name = :name AND w.lang = :lang AND w.id != :excludeId")
    boolean existsByNameAndLangExcludingId(@Param("name") String name, @Param("lang") Website.Language lang, @Param("excludeId") String excludeId);

    /**
     * Check if website name exists for a language
     */
    boolean existsByNameAndLang(String name, Website.Language lang);

    /**
     * Find top websites by display order
     */
    @Query("SELECT w FROM Website w WHERE w.isActive = true ORDER BY w.displayOrder ASC")
    List<Website> findTopActiveWebsites(Pageable pageable);
}