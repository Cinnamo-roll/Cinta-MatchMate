package com.cinoo.matchmateserver.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新增费用请求（茶/饭）。
 */
@Data
public class AddExpenseRequest {

    /** 类型：1-茶钱 2-饭钱 */
    @NotNull(message = "费用类型不能为空")
    private Integer type;

    /** 金额（分） */
    @NotNull(message = "金额不能为空")
    @jakarta.validation.constraints.Min(value = 1, message = "金额必须大于0")
    private Integer amount;

    /** 参与分摊的用户ID列表 */
    @NotEmpty(message = "至少选择一位分摊人")
    @Size(min = 1, max = 8, message = "分摊人数范围1-8")
    private List<Long> participantIds;
}
