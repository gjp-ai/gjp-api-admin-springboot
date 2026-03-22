package org.ganjp.api.bm.setting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.bm.setting.AppSetting;

import java.time.LocalDateTime;

/**
 * DTO for app setting response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppSettingResponse {

    private String id;
    private String name;
    private String value;
    private AppSetting.Language lang;
    private Boolean isSystem;
    private Boolean isPublic;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    /**
     * Convert entity to response DTO
     */
    public static AppSettingResponse fromEntity(AppSetting appSetting) {
        return AppSettingResponse.builder()
                .id(appSetting.getId())
                .name(appSetting.getName())
                .value(appSetting.getValue())
                .lang(appSetting.getLang())
                .isSystem(appSetting.getIsSystem())
                .isPublic(appSetting.getIsPublic())
                .createdAt(appSetting.getCreatedAt())
                .createdBy(appSetting.getCreatedBy())
                .updatedAt(appSetting.getUpdatedAt())
                .updatedBy(appSetting.getUpdatedBy())
                .build();
    }
}
