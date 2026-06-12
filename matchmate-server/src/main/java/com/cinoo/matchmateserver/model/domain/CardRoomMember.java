package com.cinoo.matchmateserver.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 房间成员实体。
 */
@TableName("cardRoomMember")
@Data
public class CardRoomMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private Long userId;

    private Integer status;

    private Integer totalScore;

    private Integer settleScore;

    private Integer wins;

    private Integer losses;

    private Date joinTime;

    private Date leaveTime;
}
