package com.cinoo.matchmateserver.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Current user's card-room history item.
 */
@Data
public class CardRoomHistoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long roomId;
    private String roomCode;
    private String ownerName;
    private Integer status;
    private Integer memberCount;
    private Integer score;
    private Date createTime;
    private Date settleTime;
}
