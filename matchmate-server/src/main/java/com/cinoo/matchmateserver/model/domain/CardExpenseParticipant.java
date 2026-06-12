package com.cinoo.matchmateserver.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 费用分摊明细实体。
 */
@TableName("cardExpenseParticipant")
@Data
public class CardExpenseParticipant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long expenseId;

    private Long userId;
}
