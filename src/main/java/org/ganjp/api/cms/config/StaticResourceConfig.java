package org.ganjp.api.cms.config;

import lombok.RequiredArgsConstructor;
import org.ganjp.api.cms.logo.LogoUploadProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for serving static logo files
 */
@Configuration
@RequiredArgsConstructor
public class StaticResourceConfig implements WebMvcConfigurer {

    private final LogoUploadProperties uploadProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Map /uploads/logos/** to the actual directory
        registry.addResourceHandler("/uploads/logos/**")
                .addResourceLocations("file:" + uploadProperties.getDirectory() + "/");
    }
}
