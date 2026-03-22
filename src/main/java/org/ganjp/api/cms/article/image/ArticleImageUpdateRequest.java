package org.ganjp.api.cms.article.image;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.cms.article.image.ArticleImage.Language;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleImageUpdateRequest {
    @Size(max = 36)
    private String articleId;

    @Size(max = 500)
    private String articleTitle;

    @Size(max = 500)
    private String originalUrl;

    private Language lang;

    private Integer displayOrder;

    private Boolean isActive;
}
