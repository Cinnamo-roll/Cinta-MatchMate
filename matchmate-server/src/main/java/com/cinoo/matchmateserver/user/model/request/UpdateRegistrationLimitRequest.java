package com.cinoo.matchmateserver.user.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRegistrationLimitRequest {

    @NotNull(message = "每日注册限额不能为空")
    @Min(value = 0, message = "每日注册限额不能小于 0")
    @Max(value = 1000, message = "每日注册限额不能超过 1000")
    private Integer dailyLimit;
}
