package com.cinoo.matchmateserver.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 费用记录 VO。
 */
@Data
public class CardExpenseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long expenseId;
    private Integer type;
    private Integer amount;
    private Long payerId;
    private String payerName;
    private Date createTime;
    private List<Participant> participants;

    @Data
    public static class Participant {
        private Long userId;
        private String username;
    }
}
