package com.cinoo.matchmateserver.card.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 平摊资金记录 VO。
 */
@Data
public class CardFundRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long fundId;
    private Integer type;
    /** 金额（分） */
    private Integer amount;
    private Long creatorId;
    private String creatorName;
    private Date createTime;

    private List<Participant> participants;
    private CardUndoStatusVO undoStatus;

    @Data
    public static class Participant implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long userId;
        private String username;
    }
}
