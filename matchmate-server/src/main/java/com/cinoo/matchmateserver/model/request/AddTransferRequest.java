package com.cinoo.matchmateserver.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新增收支记录请求。当前用户向其他成员转账，金额合计必须为0。
 */
@Data
public class AddTransferRequest {

    @NotNull(message = "转账列表不能为空")
    @Size(min = 1, max = 8, message = "至少转账给1人，最多8人")
    private List<TransferEntry> transfers;

    @Data
    public static class TransferEntry {
        @NotNull(message = "收款人ID不能为空")
        private Long toUserId;

        /** 金额（元），仅允许 1 到 999999 的正整数 */
        @NotNull(message = "金额不能为空")
        @DecimalMin(value = "1", message = "金额必须为正整数")
        @Digits(integer = 6, fraction = 0, message = "金额只能输入1到999999的正整数")
        private BigDecimal amount;
    }
}
