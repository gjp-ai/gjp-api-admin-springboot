package org.ganjp.api.cms.video;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoUpdateRequest {
    private String name;
    private String filename; // optional if external
    private String originalUrl;
    private String sourceName;
    private String coverImageFilename;
    // allow uploading a new cover image when updating
    private MultipartFile coverImageFile;
    private String description;
    private String tags;

    private Video.Language lang;
    private Integer displayOrder;
    private Boolean isActive;
}
