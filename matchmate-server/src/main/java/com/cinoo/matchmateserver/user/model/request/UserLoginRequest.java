package com.cinoo.matchmateserver.user.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录请求体
 *
 * @author CinnamoRoll
 */
@Schema(description = "用户登录请求体")
@Data
public class UserLoginRequest implements Serializable {
    private static final long serialVersionUID = -1L;

    @Schema(description = "用户账号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户账号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{4,16}$", message = "用户账号格式错误")
    private String userAccount;

    @Schema(description = "用户密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户密码不能为空")
    @Size(min = 8, max = 64, message = "用户密码长度必须为 8 到 64 位")
    private String userPassword;
}
