package org.ganjp.api.master.setting;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.master.setting.AppSetting;

/**
 * DTO for updating an app setting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppSettingUpdateRequest {

    @Size(max = 100, message = "Setting name must not exceed 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Setting name must contain only letters, numbers, dots, hyphens, and underscores")
    private String name;

    @Size(max = 500, message = "Setting value must not exceed 500 characters")
    private String value;

    private AppSetting.Language lang;

    private Boolean isSystem;

    private Boolean isPublic;
}
