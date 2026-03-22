package org.ganjp.api.cms.logo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for logo upload
 */
@Configuration
@ConfigurationProperties(prefix = "logo.upload")
@Data
public class LogoUploadProperties {

    /**
     * Directory where logos are stored
     */
    private String directory = "uploads/logos";

    /**
     * Maximum file size in bytes (default 5MB)
     */
    private long maxFileSize = 5242880;

    /**
     * Resize configuration
     */
    private Resize resize = new Resize();

    @Data
    public static class Resize {
        /**
         * Target size for width or height (default 256px)
         */
        private int targetSize = 256;
    }
}
