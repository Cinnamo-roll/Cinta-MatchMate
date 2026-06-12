package com.cinoo.matchmateserver.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户数据库实体。
 */
@TableName("user")
@Data
public class User {

    /**
     * 用户 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户昵称。
     */
    private String username;

    /**
     * 登录账号。
     */
    private String userAccount;

    /**
     * 头像地址。
     */
    private String avatarUrl;

    /**
     * 性别。
     */
    private Integer gender;

    /**
     * 加密后的密码。
     */
    private String userPassword;

    /**
     * 手机号。
     */
    private String phone;

    /**
     * 邮箱。
     */
    private String email;

    /**
     * 用户状态：0 表示正常。
     */
    private Integer userStatus;

    /**
     * 创建时间。
     */
    private Date createTime;

    /**
     * 更新时间。
     */
    private Date updateTime;

    /**
     * 逻辑删除标记：0 未删除，1 已删除。
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 用户角色：0 普通用户，1 管理员。
     */
    private Integer userRole;

}
