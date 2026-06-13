package com.cinoo.matchmateserver.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新增平摊资金请求。不计入排行榜积分。
 */
@Data
public class AddFundRequest {

    /** 类型：1-加钱 2-扣钱 */
    @NotNull(message = "类型不能为空")
    private Integer type;

    /** 金额（元），仅允许 1 到 999999 的正整数 */
    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "1", message = "金额必须为正整数")
    @Digits(integer = 6, fraction = 0, message = "金额只能输入1到999999的正整数")
    private BigDecimal amount;

    /** 参与分摊的用户ID列表 */
    @NotEmpty(message = "至少选择一位分摊人")
    @Size(min = 1, max = 8, message = "分摊人数范围1-8")
    private List<Long> participantIds;
}
