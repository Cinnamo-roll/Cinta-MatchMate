package com.cinoo.matchmateserver.user.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class UpdateUserProfileRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(max = 50, message = "用户名不能超过 50 个字符")
    private String username;

    @Min(value = 1, message = "性别只能为男或女")
    @Max(value = 2, message = "性别只能为男或女")
    private Integer gender;

    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱地址过长")
    private String email;
}
