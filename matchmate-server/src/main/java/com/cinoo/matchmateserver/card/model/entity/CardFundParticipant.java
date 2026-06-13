package com.cinoo.matchmateserver.card.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 平摊资金参与明细实体。
 */
@TableName("card_fund_participant")
@Data
public class CardFundParticipant {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("fund_id")
    private Long fundId;

    @TableField("user_id")
    private Long userId;
}
