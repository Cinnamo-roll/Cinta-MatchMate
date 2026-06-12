package com.cinoo.matchmateserver.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 加入房间请求。
 */
@Data
public class JoinRoomRequest {

    @NotBlank(message = "房间号不能为空")
    @Size(min = 6, max = 6, message = "房间号为6位数字")
    @Pattern(regexp = "\\d{6}", message = "房间号为6位数字")
    private String roomCode;
}
