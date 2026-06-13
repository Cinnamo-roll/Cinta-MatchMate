package com.cinoo.matchmateserver.card.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 打牌记账房间实体。
 */
@TableName("cardRoom")
@Data
public class CardRoom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roomCode;

    private Long ownerId;

    private Integer status;

    private Integer maxMembers;

    private Date settleTime;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;
}
