package org.ganjp.api.cms.article;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;

@Entity
@Table(name = "cms_article")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article {
    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(length = 255, nullable = false)
    private String title;

    @Column(length = 500)
    private String summary;

    @Lob
    @Column(columnDefinition = "longtext")
    private String content;

    @Column(name = "original_url", length = 500)
    private String originalUrl;

    @Column(name = "source_name", length = 255)
    private String sourceName;

    @Column(name = "cover_image_filename", length = 500)
    private String coverImageFilename;

    @Column(name = "cover_image_original_url", length = 500)
    private String coverImageOriginalUrl;

    @Column(length = 500)
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(length = 2, nullable = false)
    @Builder.Default
    private Article.Language lang = Article.Language.EN;

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

    public enum Language { EN, ZH }
}
