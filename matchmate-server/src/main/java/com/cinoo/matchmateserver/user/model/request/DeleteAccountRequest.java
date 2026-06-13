package com.cinoo.matchmateserver.user.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户注销账户请求体
 */
@Schema(description = "用户注销账户请求体")
@Data
public class DeleteAccountRequest implements Serializable {
    private static final long serialVersionUID = -1L;

    @Schema(description = "用户密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度必须为 8 到 64 位")
    private String userPassword;
}
