package com.cinoo.matchmateserver.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 房间成员 VO。
 */
@Data
public class CardRoomMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String avatarUrl;
    private Integer totalScore;
    private Integer status;
    private Integer wins;
    private Integer losses;
    private Date joinTime;
}
