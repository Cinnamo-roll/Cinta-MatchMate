package com.cinoo.matchmateserver.card.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.card.model.entity.CardRoomMember;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 房间成员 Mapper。
 */
public interface CardRoomMemberMapper extends BaseMapper<CardRoomMember> {

    /**
     * 查询房间内所有活跃成员。
     */
    List<CardRoomMember> selectActiveByRoomId(@Param("roomId") Long roomId);

    /**
     * 查询房间内全部成员，包括已退出和已结算成员。
     */
    List<CardRoomMember> selectByRoomId(@Param("roomId") Long roomId);

    /**
     * 批量更新 totalScore。
     */
    int updateScoreIncrement(@Param("memberId") Long memberId, @Param("delta") int delta);

    int reactivate(@Param("memberId") Long memberId);

    /**
     * 批量更新 status 和 settleScore。
     */
    int batchSettle(@Param("list") List<CardRoomMember> members);
}
