package org.ganjp.api.cms.logo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * DTO for creating a logo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoCreateRequest {

    @NotBlank(message = "Logo name is required")
    @Size(max = 255, message = "Logo name must not exceed 255 characters")
    private String name;

    @Pattern(regexp = "^https?://.*", message = "Original URL must be a valid HTTP/HTTPS URL")
    @Size(max = 500, message = "Original URL must not exceed 500 characters")
    private String originalUrl;

    private MultipartFile file;

    @Size(max = 500, message = "Tags must not exceed 500 characters")
    private String tags;

    @Builder.Default
    private Logo.Language lang = Logo.Language.EN;

    @Builder.Default
    private Integer displayOrder = 0;

    @Builder.Default
    private Boolean isActive = true;

    /**
     * Validate that either file or originalUrl is provided
     */
    public boolean hasImageSource() {
        return (file != null && !file.isEmpty()) || (originalUrl != null && !originalUrl.trim().isEmpty());
    }
}
