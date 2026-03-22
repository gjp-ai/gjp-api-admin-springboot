package org.ganjp.api.cms.video;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "video.upload")
public class VideoUploadProperties {
    private String directory;
    private Long maxFileSize;
    private CoverImage coverImage = new CoverImage();

    public static class CoverImage {
        private int maxSize = 600;
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
    }

    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }
    public Long getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(Long maxFileSize) { this.maxFileSize = maxFileSize; }
    public CoverImage getCoverImage() { return coverImage; }
    public void setCoverImage(CoverImage coverImage) { this.coverImage = coverImage; }
}
