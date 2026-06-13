package com.cinoo.matchmateserver.card.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 牌局记录 VO。
 */
@Data
public class CardRoundVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long roundId;
    private Integer roundNo;
    private Long creatorId;
    private Date createTime;
    private List<ScoreEntry> scores;
    private CardUndoStatusVO undoStatus;

    @Data
    public static class ScoreEntry {
        private Long userId;
        private String username;
        private Integer score;
    }
}
