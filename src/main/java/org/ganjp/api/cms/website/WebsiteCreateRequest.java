package org.ganjp.api.cms.website;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.cms.website.Website;

/**
 * DTO for creating a new website
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebsiteCreateRequest {

    @NotBlank(message = "Website name is required")
    @Size(max = 128, message = "Website name must not exceed 128 characters")
    private String name;

    @NotBlank(message = "Website URL is required")
    @Size(max = 500, message = "Website URL must not exceed 500 characters")
    private String url;

    @Size(max = 500, message = "Logo URL must not exceed 500 characters")
    private String logoUrl;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Size(max = 500, message = "Tags must not exceed 500 characters")
    private String tags;

    @NotNull(message = "Language is required")
    private Website.Language lang;

    @Min(value = 0, message = "Display order must be non-negative")
    @Builder.Default
    private Integer displayOrder = 0;

    @Builder.Default
    private Boolean isActive = true;
}
