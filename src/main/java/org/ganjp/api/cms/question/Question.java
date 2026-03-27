package org.ganjp.api.cms.question;

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
 * Question Entity for CMS
 */
@Slf4j
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cms_question")
public class Question extends BaseEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "question", length = 255, nullable = false)
    private String question;

    @Column(name = "answer", length = 2000)
    private String answer;

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
     * Check if question is active
     */
    public boolean isActiveQuestion() {
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
        Question that = (Question) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
