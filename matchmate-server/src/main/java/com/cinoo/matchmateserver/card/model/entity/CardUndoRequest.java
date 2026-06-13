package com.cinoo.matchmateserver.card.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName("card_undo_request")
@Data
public class CardUndoRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("room_id")
    private Long roomId;

    @TableField("target_type")
    private Integer targetType;

    @TableField("target_id")
    private Long targetId;

    @TableField("requester_id")
    private Long requesterId;

    private Integer status;

    @TableField("create_time")
    private Date createTime;

    @TableField("done_time")
    private Date doneTime;
}
