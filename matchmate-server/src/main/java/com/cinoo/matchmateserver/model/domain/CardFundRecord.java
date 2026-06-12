package com.cinoo.matchmateserver.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 平摊资金记录实体。
 */
@TableName("cardFundRecord")
@Data
public class CardFundRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    /** {@link com.cinoo.matchmateserver.constant.CardConstant#FUND_TYPE_ADD} or {@link com.cinoo.matchmateserver.constant.CardConstant#FUND_TYPE_DEDUCT} */
    private Integer type;

    /** 金额（分） */
    private Integer amount;

    private Long creatorId;

    private Date createTime;
}
