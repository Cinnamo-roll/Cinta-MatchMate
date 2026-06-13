package com.cinoo.matchmateserver.card.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 牌局分数实体。
 */
@TableName("cardRoundScore")
@Data
public class CardRoundScore {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roundId;

    private Long userId;

    private Integer score;
}
