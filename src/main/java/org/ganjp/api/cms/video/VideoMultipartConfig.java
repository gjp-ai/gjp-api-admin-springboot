package org.ganjp.api.cms.video;

import jakarta.servlet.MultipartConfigElement;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
@RequiredArgsConstructor
public class VideoMultipartConfig {
    private final VideoUploadProperties videoUploadProperties;

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        Long max = videoUploadProperties.getMaxFileSize();
        if (max != null && max > 0) {
            DataSize ds = DataSize.ofBytes(max);
            factory.setMaxFileSize(ds);
            factory.setMaxRequestSize(ds);
        }
        return factory.createMultipartConfig();
    }
}
