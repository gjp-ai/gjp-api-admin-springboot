package org.ganjp.api.cms.logo;

import org.ganjp.api.cms.logo.Logo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Logo entity
 */
@Repository
public interface LogoRepository extends JpaRepository<Logo, String> {
    /**
     * Flexible search by name, language, tags, and status
     */
    @Query("SELECT l FROM Logo l WHERE " +
        "(:name IS NULL OR LOWER(l.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
        "(:lang IS NULL OR l.lang = :lang) AND " +
        "(:tags IS NULL OR l.tags LIKE CONCAT('%', :tags, '%')) AND " +
        "(:isActive IS NULL OR l.isActive = :isActive)")
    Page<Logo> searchLogos(@Param("name") String name,
               @Param("lang") Logo.Language lang,
               @Param("tags") String tags,
               @Param("isActive") Boolean isActive,
               Pageable pageable);

    /**
     * Flexible search by name, language, tags, and status
     */
    @Query("SELECT l FROM Logo l WHERE " +
        "(:name IS NULL OR LOWER(l.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
        "(:lang IS NULL OR l.lang = :lang) AND " +
        "(:tags IS NULL OR l.tags LIKE CONCAT('%', :tags, '%')) AND " +
        "(:isActive IS NULL OR l.isActive = :isActive) " +
        "ORDER BY l.displayOrder")
    List<Logo> searchLogos(@Param("name") String name,
               @Param("lang") Logo.Language lang,
               @Param("tags") String tags,
               @Param("isActive") Boolean isActive);

    /**
     * Find all active logos ordered by display order
     */
    List<Logo> findByIsActiveTrueOrderByDisplayOrderAsc();

    /**
     * Find logo by ID and active status
     */
    Optional<Logo> findByIdAndIsActiveTrue(String id);

    /**
     * Find logos by name containing keyword (case insensitive)
     */
    @Query("SELECT l FROM Logo l WHERE LOWER(l.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND l.isActive = true ORDER BY l.displayOrder")
    List<Logo> searchByNameContaining(@Param("keyword") String keyword);

    /**
     * Find logos by tag
     */
    @Query("SELECT l FROM Logo l WHERE l.tags LIKE CONCAT('%', :tag, '%') AND l.isActive = true ORDER BY l.displayOrder")
    List<Logo> findByTagsContaining(@Param("tag") String tag);

    /**
     * Check if logo with filename exists
     */
    boolean existsByFilename(String filename);

    /**
     * Find logo by filename and active status
     */
    Optional<Logo> findByFilenameAndIsActiveTrue(String filename);
}
