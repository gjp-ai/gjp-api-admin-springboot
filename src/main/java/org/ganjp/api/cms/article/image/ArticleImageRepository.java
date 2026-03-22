package org.ganjp.api.cms.article.image;

import org.ganjp.api.cms.article.image.ArticleImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ArticleImageRepository extends JpaRepository<ArticleImage, String> {
    List<ArticleImage> findByArticleIdAndIsActiveTrueOrderByDisplayOrderAsc(String articleId);

    Optional<ArticleImage> findByIdAndIsActiveTrue(String id);

    boolean existsByFilename(String filename);

    @Query("SELECT i FROM ArticleImage i WHERE " +
        "(:articleId IS NULL OR i.articleId = :articleId) AND " +
        "(:lang IS NULL OR i.lang = :lang) AND " +
        "(:isActive IS NULL OR i.isActive = :isActive) " +
        "ORDER BY i.displayOrder")
    List<ArticleImage> searchArticleImages(@Param("articleId") String articleId,
                             @Param("lang") ArticleImage.Language lang,
                             @Param("isActive") Boolean isActive);
}
