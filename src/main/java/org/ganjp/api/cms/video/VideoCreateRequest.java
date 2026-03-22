package org.ganjp.api.cms.video;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoCreateRequest {
    private String name;
    private String filename;
    private MultipartFile file;
    private String originalUrl;
    private String sourceName;
    private String coverImageFilename;
    private MultipartFile coverImageFile;
    private String description;
    private String tags;
    private Video.Language lang;
    private Integer displayOrder;
    private Boolean isActive;
}
