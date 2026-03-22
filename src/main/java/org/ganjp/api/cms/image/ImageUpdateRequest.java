package org.ganjp.api.cms.image;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.cms.image.Image.Language;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUpdateRequest {
    @Size(max = 255, message = "Image name must not exceed 255 characters")
    private String name;

    @Size(max = 1000)
    private String originalUrl;

    @Size(max = 255)
    private String sourceName;

    @Size(max = 255)
    private String filename;

    @Size(max = 255)
    private String thumbnailFilename;

    @Size(max = 10)
    private String extension;

    @Size(max = 100)
    private String mimeType;

    @Size(max = 500)
    private String altText;

    @Size(max = 500)
    private String tags;

    private Language lang;

    private Integer displayOrder;

    private Boolean isActive;
}
