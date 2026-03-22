package org.ganjp.api.cms.file;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Timestamp;

@Entity
@Table(name = "cms_file")
@Data
public class File {
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
    private Language lang = Language.EN;

    @Column(name = "display_order")
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
    private Boolean isActive = true;
}
