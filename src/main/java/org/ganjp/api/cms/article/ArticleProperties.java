package org.ganjp.api.cms.article;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "article")
@Data
public class ArticleProperties {
    private CoverImage coverImage;
    private ContentImage contentImage;

    @Data
    public static class CoverImage {
        private String baseUrl;
        private Upload upload;
    }

    @Data
    public static class ContentImage {
        private String baseUrl;
        private Upload upload;
    }

    @Data
    public static class Upload {
        private String directory;
        private Long maxFileSize;
        private Resize resize;
    }

    @Data
    public static class Resize {
        private Integer maxSize;
    }
}
