package com.cinoo.matchmateserver.card.service.support;

import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.exception.BusinessException;

import java.math.BigDecimal;

public final class CardAmountValidator {

    private static final int MAX_YUAN = 999_999;
    private static final int FEN_PER_YUAN = 100;
    private static final String INTEGER_YUAN_MESSAGE = "金额只能输入 1 到 999999 的正整数";

    private CardAmountValidator() {
    }

    public static int requirePositiveIntegerYuan(BigDecimal amount) {
        if (amount == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "金额不能为空");
        }
        try {
            int value = amount.intValueExact();
            if (value <= 0 || value > MAX_YUAN) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, INTEGER_YUAN_MESSAGE);
            }
            return value;
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, INTEGER_YUAN_MESSAGE);
        }
    }

    public static int requirePositiveIntegerYuanAsFen(BigDecimal amount) {
        return Math.multiplyExact(requirePositiveIntegerYuan(amount), FEN_PER_YUAN);
    }
}
