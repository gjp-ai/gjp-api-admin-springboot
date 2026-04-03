package org.ganjp.api.cms.audio;

import jakarta.persistence.*;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.common.model.BaseEntity;

import java.util.Objects;

@Slf4j
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cms_audio")
public class Audio extends BaseEntity {
    @Id
    @Column(columnDefinition = "char(36)", nullable = false)
    private String id;

    @Column(length = 255, nullable = false)
    private String name;

    @Column(length = 255)
    private String filename;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "cover_image_filename", length = 500)
    private String coverImageFilename;

    @Column(name = "original_url", length = 500)
    private String originalUrl;

    @Column(name = "source_name", length = 255)
    private String sourceName;

    @Column(length = 500)
    private String description;
    @Column(columnDefinition = "text")
    private String subtitle;

    @Column(length = 255)
    private String artist;

    @Column(length = 500)
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(length = 2, nullable = false)
    @Builder.Default
    private Language lang = Language.EN;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "download_status", length = 20)
    private DownloadStatus downloadStatus;

    @Column(name = "download_error", length = 500)
    private String downloadError;

    public enum Language {
        EN, ZH
    }

    public enum DownloadStatus { PENDING, DOWNLOADING, COMPLETED, FAILED }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Audio that = (Audio) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
