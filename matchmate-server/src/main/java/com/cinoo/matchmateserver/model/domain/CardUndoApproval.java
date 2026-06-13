package com.cinoo.matchmateserver.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName("card_undo_approval")
@Data
public class CardUndoApproval {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("request_id")
    private Long requestId;

    @TableField("user_id")
    private Long userId;

    @TableField("create_time")
    private Date createTime;
}
