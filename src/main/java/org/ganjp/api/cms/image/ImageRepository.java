package org.ganjp.api.cms.image;

import org.ganjp.api.cms.image.Image;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ImageRepository extends JpaRepository<Image, String> {
    @Query("SELECT i FROM Image i WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND i.isActive = true")
    Page<Image> searchByNameContaining(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT i FROM Image i WHERE i.tags LIKE CONCAT('%', :tag, '%') AND i.isActive = true ORDER BY i.displayOrder")
    List<Image> findByTagsContaining(@Param("tag") String tag);

    @Query("SELECT i FROM Image i WHERE " +
        "(:name IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
        "(:lang IS NULL OR i.lang = :lang) AND " +
        "(:tags IS NULL OR i.tags LIKE CONCAT('%', :tags, '%')) AND " +
        "(:isActive IS NULL OR i.isActive = :isActive)")
    Page<Image> searchImages(@Param("name") String name,
                             @Param("lang") Image.Language lang,
                             @Param("tags") String tags,
                             @Param("isActive") Boolean isActive,
                             Pageable pageable);

    List<Image> findByIsActiveTrueOrderByDisplayOrderAsc();

    Optional<Image> findByIdAndIsActiveTrue(String id);

    boolean existsByFilename(String filename);
    boolean existsByThumbnailFilename(String thumbnailFilename);

    @Query("SELECT i FROM Image i WHERE (i.filename = :name OR i.thumbnailFilename = :name) AND i.isActive = true")
    Optional<Image> findByFilenameAndIsActiveTrue(@Param("name") String name);
}
