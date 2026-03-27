package org.ganjp.api.cms.file;

import jakarta.persistence.*;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.sql.Timestamp;
import java.util.Objects;

@Slf4j
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cms_file")
public class FileAsset {
    @Id
    private String id;

    private String name;

    @Column(name = "original_url")
    private String originalUrl;

    @Column(name = "source_name")
    private String sourceName;

    private String filename;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    private String extension;

    @Column(name = "mime_type")
    private String mimeType;

    private String tags;

    public enum Language { EN, ZH }

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Language lang = Language.EN;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileAsset that = (FileAsset) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
