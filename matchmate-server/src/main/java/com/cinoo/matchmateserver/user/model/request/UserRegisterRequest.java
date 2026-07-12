package com.cinoo.matchmateserver.user.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册请求体
 *
 * @author CintaOvO
 */
@Schema(description = "用户注册请求体")
@Data
public class UserRegisterRequest implements Serializable {
    private static final long serialVersionUID = -1L;

    @Schema(description = "用户账号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户账号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{4,16}$", message = "用户账号只能包含 4 到 16 位字母或数字")
    private String userAccount;

    @Schema(description = "用户密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户密码不能为空")
    @Size(min = 8, max = 64, message = "用户密码长度必须为 8 到 64 位")
    private String userPassword;

    @Schema(description = "再次输入的密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "确认密码不能为空")
    @Size(min = 8, max = 64, message = "确认密码长度必须为 8 到 64 位")
    private String checkPassword;

}
