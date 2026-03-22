package org.ganjp.api.cms.logo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for logo response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoResponse {

    private String id;
    private String name;
    private String originalUrl;
    private String filename;
    private String extension;
    private String tags;
    private Logo.Language lang;
    private Integer displayOrder;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    /**
     * Get tags as array
     */
    @JsonIgnore
    public String[] getTagsArray() {
        if (tags == null || tags.trim().isEmpty()) {
            return new String[0];
        }
        return tags.split(",");
    }
}
