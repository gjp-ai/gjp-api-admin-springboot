package org.ganjp.api.cms.article.image;

import jakarta.persistence.*;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.sql.Timestamp;
import java.util.Objects;

@Slf4j
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cms_article_image")
public class ArticleImage {
    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "article_id", length = 36, nullable = false)
    private String articleId;

    @Column(name = "article_title", length = 500)
    private String articleTitle;

    @Column(length = 255, nullable = false)
    private String filename;

    @Column(name = "original_url", length = 500)
    private String originalUrl;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Enumerated(EnumType.STRING)
    @Column(length = 2, nullable = false)
    @Builder.Default
    private Language lang = Language.EN;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    public enum Language {
        EN, ZH
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArticleImage that = (ArticleImage) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
