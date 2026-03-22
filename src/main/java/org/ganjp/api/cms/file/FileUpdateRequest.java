package org.ganjp.api.cms.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.cms.file.FileAsset;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUpdateRequest {
    private String name;
    private String originalUrl;
    private String sourceName;
    private MultipartFile file;
    private String filename; // optional desired filename
    private String tags;
    private FileAsset.Language lang;
    private Integer displayOrder;
    private Boolean isActive;
}
