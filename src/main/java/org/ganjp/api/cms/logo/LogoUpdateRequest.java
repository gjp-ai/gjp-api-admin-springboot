package org.ganjp.api.cms.logo;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating a logo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoUpdateRequest {

    @Size(max = 255, message = "Logo name must not exceed 255 characters")
    private String name;

    @Pattern(regexp = "^https?://.*", message = "Original URL must be a valid HTTP/HTTPS URL")
    @Size(max = 500, message = "Original URL must not exceed 500 characters")
    private String originalUrl;

    @Pattern(regexp = "^(png|jpg|jpeg|gif|svg|webp|bmp)$", message = "Extension must be one of: png, jpg, jpeg, gif, svg, webp, bmp")
    @Size(max = 16, message = "Extension must not exceed 16 characters")
    private String extension;

    @Size(max = 500, message = "Tags must not exceed 500 characters")
    private String tags;

    private Logo.Language lang;

    private Integer displayOrder;

    private Boolean isActive;
}
