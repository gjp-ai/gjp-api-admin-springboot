package org.ganjp.api.cms.article.image;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.cms.article.image.ArticleImage.Language;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleImageResponse {
    private String id;
    private String articleId;
    private String articleTitle;
    private String filename;
    private String fileUrl;
    private String originalUrl;
    private Integer width;
    private Integer height;
    private Language lang;
    private Integer displayOrder;
    private String createdBy;
    private String updatedBy;
    private Boolean isActive;
    private String createdAt;
    private String updatedAt;
}
