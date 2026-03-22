package org.ganjp.api.cms.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.cms.file.File;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileResponse {
    private String id;
    private String name;
    private String originalUrl;
    private String sourceName;
    private String filename;
    private Long sizeBytes;
    private String extension;
    private String mimeType;
    private String tags;
    private File.Language lang;
    private Integer displayOrder;
    private String createdAt;
    private String updatedAt;
    private String createdBy;
    private String updatedBy;
    private Boolean isActive;
}
