package com.cinoo.matchmateserver.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 平摊资金参与明细实体。
 */
@TableName("cardFundParticipant")
@Data
public class CardFundParticipant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fundId;

    private Long userId;
}
