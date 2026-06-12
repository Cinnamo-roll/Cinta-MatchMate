package com.cinoo.matchmateserver.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新增转账牌局请求。当前用户向其他成员转账，总分必须为0。
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

        /** 金额（元），>=0，最多1位小数 */
        @NotNull(message = "金额不能为空")
        private BigDecimal amount;
    }
}
