package com.cinoo.matchmateserver.tag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName("user_tag")
@Data
public class UserTag {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long tagId;
    private Date createTime;
}
