package com.cinoo.matchmateserver.user.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserStatusRequest {

    @NotNull(message = "用户状态不能为空")
    @Min(value = 0, message = "用户状态不合法")
    @Max(value = 1, message = "用户状态不合法")
    private Integer userStatus;
}
