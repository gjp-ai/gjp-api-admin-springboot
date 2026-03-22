package org.ganjp.api.cms.article;

import org.ganjp.api.cms.article.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, String> {
    Optional<Article> findByIdAndIsActiveTrue(String id);

    @Query("SELECT a FROM Article a WHERE " +
        "(:title IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
        "(:lang IS NULL OR a.lang = :lang) AND " +
        "(:tags IS NULL OR a.tags LIKE CONCAT('%', :tags, '%')) AND " +
        "(:isActive IS NULL OR a.isActive = :isActive)")
    Page<Article> searchArticles(@Param("title") String title,
                 @Param("lang") Article.Language lang,
                 @Param("tags") String tags,
                 @Param("isActive") Boolean isActive,
                 Pageable pageable);

    /**
     * Check whether an article exists with the given cover image filename.
     * This is used by public file-serving endpoints to ensure the file is associated with an active article.
     */
    boolean existsByCoverImageFilename(String filename);

    default boolean existsByFilename(String filename) {
        return existsByCoverImageFilename(filename);
    }
}
