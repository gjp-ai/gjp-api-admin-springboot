package org.ganjp.api.cms.image;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "image.upload")
public class ImageUploadProperties {
    private String directory;
    private Long maxFileSize;
    private Resize resize;

    public static class Resize {
        private Integer maxSize;
        private Integer thumbnailSize;
        public Integer getMaxSize() { return maxSize; }
        public void setMaxSize(Integer maxSize) { this.maxSize = maxSize; }
        public Integer getThumbnailSize() { return thumbnailSize; }
        public void setThumbnailSize(Integer thumbnailSize) { this.thumbnailSize = thumbnailSize; }
    }

    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }
    public Long getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(Long maxFileSize) { this.maxFileSize = maxFileSize; }
    public Resize getResize() { return resize; }
    public void setResize(Resize resize) { this.resize = resize; }
}
