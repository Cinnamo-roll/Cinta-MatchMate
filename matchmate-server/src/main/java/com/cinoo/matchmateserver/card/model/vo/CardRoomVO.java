package com.cinoo.matchmateserver.card.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 房间详情 VO。
 */
@Data
public class CardRoomVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long roomId;
    private String roomCode;
    private Long ownerId;
    private String ownerName;
    private Integer status;
    private Integer maxMembers;
    private Date settleTime;
    private Date createTime;

    private List<CardRoomMemberVO> members;
    private List<CardRoundVO> recentRounds;
    private List<CardFundRecordVO> recentFunds;
    /** 当前用户在该房间的平摊资金余额（分），正数表示应收回，负数表示应付出 */
    private Integer fundBalance;
}
