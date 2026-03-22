package org.ganjp.api.cms.audio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioCreateRequest {
    private String name;
    private String filename;
    private MultipartFile file;
    private String originalUrl;
    private String sourceName;
    private String coverImageFilename;
    private MultipartFile coverImageFile;
    private String description;
    private String subtitle;
    private String artist;
    private String tags;
    private Audio.Language lang;
    private Integer displayOrder;
    private Boolean isActive;
}
