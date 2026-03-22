package org.ganjp.api.cms.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.cms.article.Article;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleResponse {
    private String id;
    private String title;
    private String summary;
    private String content;
    private String originalUrl;
    private String sourceName;
    private String coverImageFilename;
    private String coverImageOriginalUrl;
    private String tags;
    private Article.Language lang;
    private Integer displayOrder;
    private String createdBy;
    private String updatedBy;
    private Boolean isActive;
    private String createdAt;
    private String updatedAt;
}
