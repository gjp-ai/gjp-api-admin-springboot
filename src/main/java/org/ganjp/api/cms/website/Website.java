package org.ganjp.api.cms.website;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import org.ganjp.api.common.model.BaseEntity;

import java.util.Objects;

/**
 * Website Entity for CMS
 */
@Slf4j
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cms_website")
public class Website extends BaseEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "url", length = 500, nullable = false)
    private String url;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "tags", length = 500)
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "lang", nullable = false)
    @Builder.Default
    private Language lang = Language.EN;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Language enumeration
     */
    public enum Language {
        EN,
        ZH
    }

    /**
     * Check if website is active
     */
    public boolean isActiveWebsite() {
        return isActive != null && isActive;
    }

    /**
     * Get tags as array
     */
    public String[] getTagsArray() {
        if (tags == null || tags.trim().isEmpty()) {
            return new String[0];
        }
        return tags.split(",");
    }

    /**
     * Set tags from array
     */
    public void setTagsFromArray(String[] tagsArray) {
        if (tagsArray == null || tagsArray.length == 0) {
            this.tags = null;
        } else {
            this.tags = String.join(",", tagsArray);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Website that = (Website) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
