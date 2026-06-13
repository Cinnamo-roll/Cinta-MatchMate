package com.cinoo.matchmateserver.card.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 牌局记录实体。
 */
@TableName("cardRound")
@Data
public class CardRound {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private Integer roundNo;

    private Integer settled;

    private Long creatorId;

    private Date createTime;
}
