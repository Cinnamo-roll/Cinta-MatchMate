package com.cinoo.matchmateserver.card.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class CardUndoStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long requestId;
    private Long requesterId;
    private String requesterName;
    private Integer approvedCount;
    private Integer requiredCount;
    private Boolean approvedByMe;
    private Boolean canApprove;
}
