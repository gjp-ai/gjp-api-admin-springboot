package org.ganjp.api.cms.audio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.cms.audio.Audio;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioResponse {
    private String id;
    private String name;
    private String filename;
    private Long sizeBytes;
    private String coverImageFilename;
    private String originalUrl;
    private String sourceName;
    private String subtitle;
    private String description;
    private String artist;
    private String tags;
    private Audio.Language lang;
    private Integer displayOrder;
    private String createdBy;
    private String updatedBy;
    private Boolean isActive;
    private String createdAt;
    private String updatedAt;
}
