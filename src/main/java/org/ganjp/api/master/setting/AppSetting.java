package org.ganjp.api.master.setting;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.ganjp.api.common.model.BaseEntity;

/**
 * Application Settings Entity with internationalization support
 */
@Entity
@Table(name = "master_app_settings")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppSetting extends BaseEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "value", length = 500)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "lang", nullable = false)
    @Builder.Default
    private Language lang = Language.EN;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private Boolean isSystem = false;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    /**
     * Language enumeration
     */
    public enum Language {
        EN,
        ZH
    }

    /**
     * Check if setting is user-editable
     */
    public boolean isUserEditable() {
        return !isSystem;
    }

    /**
     * Check if setting is visible to public users
     */
    public boolean isPublicVisible() {
        return isPublic;
    }
}
