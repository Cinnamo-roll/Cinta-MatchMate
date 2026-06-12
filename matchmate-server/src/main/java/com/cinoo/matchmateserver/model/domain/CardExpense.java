package com.cinoo.matchmateserver.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 费用记录实体（茶/饭）。
 */
@TableName("cardExpense")
@Data
public class CardExpense {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    /** {@link com.cinoo.matchmateserver.constant.CardConstant#EXPENSE_TYPE_TEA} or {@link com.cinoo.matchmateserver.constant.CardConstant#EXPENSE_TYPE_MEAL} */
    private Integer type;

    private Integer amount;

    private Long payerId;

    private Date createTime;
}
