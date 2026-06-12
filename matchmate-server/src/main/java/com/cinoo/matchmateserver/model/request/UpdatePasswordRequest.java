package com.cinoo.matchmateserver.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改当前用户密码请求体。
 */
@Schema(description = "修改当前用户密码请求体")
@Data
public class UpdatePasswordRequest implements Serializable {
    private static final long serialVersionUID = -1L;

    @Schema(description = "当前密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "当前密码不能为空")
    @Size(min = 8, max = 64, message = "当前密码长度必须为 8 到 64 位")
    private String currentPassword;

    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "新密码长度必须为 8 到 64 位")
    private String newPassword;

    @Schema(description = "确认新密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "确认密码不能为空")
    @Size(min = 8, max = 64, message = "确认密码长度必须为 8 到 64 位")
    private String checkPassword;
}
