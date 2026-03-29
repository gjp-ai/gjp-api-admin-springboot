package org.ganjp.api.cms.video;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "video.upload")
public class VideoUploadProperties {
    private String directory;
    private Long maxFileSize;
    private CoverImage coverImage = new CoverImage();
    private Download download = new Download();

    public static class CoverImage {
        private int maxSize = 600;
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
    }

    public static class Download {
        private int maxResolution = 720;
        public int getMaxResolution() { return maxResolution; }
        public void setMaxResolution(int maxResolution) { this.maxResolution = maxResolution; }
    }

    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }
    public Long getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(Long maxFileSize) { this.maxFileSize = maxFileSize; }
    public CoverImage getCoverImage() { return coverImage; }
    public void setCoverImage(CoverImage coverImage) { this.coverImage = coverImage; }
    public Download getDownload() { return download; }
    public void setDownload(Download download) { this.download = download; }
}
