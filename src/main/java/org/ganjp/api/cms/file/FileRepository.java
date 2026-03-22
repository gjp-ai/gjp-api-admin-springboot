package org.ganjp.api.cms.file;

import org.ganjp.api.cms.file.FileAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<FileAsset, String> {
    Optional<FileAsset> findByIdAndIsActiveTrue(String id);

    @Query("SELECT f FROM FileAsset f WHERE " +
            "(:name IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:lang IS NULL OR f.lang = :lang) AND " +
            "(:tags IS NULL OR f.tags LIKE CONCAT('%', :tags, '%')) AND " +
            "(:isActive IS NULL OR f.isActive = :isActive)")
    Page<FileAsset> searchFiles(@Param("name") String name,
                           @Param("lang") FileAsset.Language lang,
                           @Param("tags") String tags,
                           @Param("isActive") Boolean isActive,
                           Pageable pageable);

    @Query("SELECT f FROM FileAsset f WHERE " +
            "(:name IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:lang IS NULL OR f.lang = :lang) AND " +
            "(:tags IS NULL OR f.tags LIKE CONCAT('%', :tags, '%')) AND " +
            "(:isActive IS NULL OR f.isActive = :isActive) " +
            "ORDER BY f.displayOrder")
    List<FileAsset> searchFiles(@Param("name") String name,
                           @Param("lang") FileAsset.Language lang,
                           @Param("tags") String tags,
                           @Param("isActive") Boolean isActive);

    boolean existsByFilename(String filename);
}
