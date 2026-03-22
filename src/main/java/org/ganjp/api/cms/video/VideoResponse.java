package org.ganjp.api.cms.video;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoResponse {
    private String id;
    private String name;
    private String filename;
    private Long sizeBytes;
    private String coverImageFilename;
    private String originalUrl;
    private String sourceName;
    private String description;
    private String tags;
    private Video.Language lang;
    private Integer displayOrder;
    private String createdBy;
    private String updatedBy;
    private Boolean isActive;
    private String createdAt;
    private String updatedAt;
}
