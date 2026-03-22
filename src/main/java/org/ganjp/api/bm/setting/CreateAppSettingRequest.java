package org.ganjp.api.bm.setting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.bm.setting.AppSetting;

/**
 * DTO for creating a new app setting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAppSettingRequest {

    @NotBlank(message = "Setting name is required")
    @Size(max = 50, message = "Setting name must not exceed 50 characters")
    private String name;

    @Size(max = 500, message = "Setting value must not exceed 500 characters")
    private String value;

    @NotNull(message = "Language is required")
    private AppSetting.Language lang;

    @Builder.Default
    private Boolean isSystem = false;

    @Builder.Default
    private Boolean isPublic = false;
}
