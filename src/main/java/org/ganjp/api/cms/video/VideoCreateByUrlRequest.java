package org.ganjp.api.cms.video;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a video from a URL (including YouTube).
 * Accepts JSON body (not multipart).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoCreateByUrlRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Original URL is required")
    @Size(max = 500, message = "Original URL must not exceed 500 characters")
    private String originalUrl;

    @Size(max = 255, message = "Filename must not exceed 255 characters")
    private String filename;

    @Size(max = 255, message = "Source name must not exceed 255 characters")
    private String sourceName;

    @Size(max = 255, message = "Cover image filename must not exceed 255 characters")
    private String coverImageFilename;

    @Size(max = 500, message = "Cover image URL must not exceed 500 characters")
    private String coverImageUrl;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 500, message = "Tags must not exceed 500 characters")
    private String tags;

    private Video.Language lang;
    private Integer displayOrder;
    private Boolean isActive;
}
