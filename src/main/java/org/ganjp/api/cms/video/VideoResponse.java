package org.ganjp.api.cms.video;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoResponse {
    private String id;
    private String name;
    private String filename;
    private String fileUrl;
    private Long sizeBytes;
    private String coverImageFilename;
    private String coverImageUrl;
    private String originalUrl;
    private String sourceName;
    private String description;
    private String tags;
    private Video.Language lang;
    private Integer displayOrder;
    private String createdBy;
    private String updatedBy;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VideoResponse from(Video video, String baseUrl) {
        String fileUrl = null;
        if (video.getFilename() != null) {
            fileUrl = baseUrl + "/v1/videos/view/" + video.getFilename();
        }
        String coverUrl = null;
        if (video.getCoverImageFilename() != null) {
            coverUrl = baseUrl + "/v1/videos/cover/" + video.getCoverImageFilename();
        }
        return VideoResponse.builder()
                .id(video.getId())
                .name(video.getName())
                .filename(video.getFilename())
                .fileUrl(fileUrl)
                .sizeBytes(video.getSizeBytes())
                .coverImageFilename(video.getCoverImageFilename())
                .coverImageUrl(coverUrl)
                .originalUrl(video.getOriginalUrl())
                .sourceName(video.getSourceName())
                .description(video.getDescription())
                .tags(video.getTags())
                .lang(video.getLang())
                .displayOrder(video.getDisplayOrder())
                .createdBy(video.getCreatedBy())
                .updatedBy(video.getUpdatedBy())
                .isActive(video.getIsActive())
                .createdAt(video.getCreatedAt())
                .updatedAt(video.getUpdatedAt())
                .build();
    }
}
