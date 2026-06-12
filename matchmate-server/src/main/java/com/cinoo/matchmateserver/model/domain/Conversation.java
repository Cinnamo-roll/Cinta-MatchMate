package com.cinoo.matchmateserver.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName("conversation")
@Data
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId1;

    private Long userId2;

    private String lastMessage;

    private Date lastMessageTime;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;
}
