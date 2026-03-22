package org.ganjp.api.cms.video;

import org.ganjp.api.cms.video.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, String> {
    Optional<Video> findByIdAndIsActiveTrue(String id);

    @Query("SELECT v FROM Video v WHERE " +
            "(:name IS NULL OR LOWER(v.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:lang IS NULL OR v.lang = :lang) AND " +
            "(:tags IS NULL OR v.tags LIKE CONCAT('%', :tags, '%')) AND " +
            "(:isActive IS NULL OR v.isActive = :isActive)")
    Page<Video> searchVideos(@Param("name") String name,
                             @Param("lang") Video.Language lang,
                             @Param("tags") String tags,
                             @Param("isActive") Boolean isActive,
                             Pageable pageable);

    @Query("SELECT v FROM Video v WHERE " +
            "(:name IS NULL OR LOWER(v.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:lang IS NULL OR v.lang = :lang) AND " +
            "(:tags IS NULL OR v.tags LIKE CONCAT('%', :tags, '%')) AND " +
            "(:isActive IS NULL OR v.isActive = :isActive) " +
            "ORDER BY v.displayOrder")
    List<Video> searchVideos(@Param("name") String name,
                             @Param("lang") Video.Language lang,
                             @Param("tags") String tags,
                             @Param("isActive") Boolean isActive);
    boolean existsByFilenameOrCoverImageFilename(String filename, String coverImageFilename);

    // Keep the original method name for callers, but check both filename and cover image filename
    default boolean existsByFilename(String filename) {
        return existsByFilenameOrCoverImageFilename(filename, filename);
    }
}
