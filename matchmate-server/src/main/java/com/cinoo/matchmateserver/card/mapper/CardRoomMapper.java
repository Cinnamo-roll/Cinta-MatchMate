package com.cinoo.matchmateserver.card.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.card.model.entity.CardRoom;
import com.cinoo.matchmateserver.card.model.vo.CardRoomHistoryVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 房间 Mapper。
 */
public interface CardRoomMapper extends BaseMapper<CardRoom> {

    /**
     * 查询用户当前活跃的房间。
     */
    CardRoom selectActiveRoomByUserId(@Param("userId") Long userId);

    /**
     * 查询房间内所有成员ID（status=0）。
     */
    List<Long> selectActiveMemberIds(@Param("roomId") Long roomId);

    /**
     * 查询用户参与过的房间记录。
     */
    List<CardRoomHistoryVO> selectHistoryByUserId(
            @Param("userId") Long userId,
            @Param("limit") int limit);

    List<Long> selectExpiredEndedRoomIds(@Param("retainCount") int retainCount);

    int deleteFundParticipantsByRoomIds(@Param("roomIds") List<Long> roomIds);

    int deleteFundsByRoomIds(@Param("roomIds") List<Long> roomIds);

    int deleteRoundScoresByRoomIds(@Param("roomIds") List<Long> roomIds);

    int deleteRoundsByRoomIds(@Param("roomIds") List<Long> roomIds);

    int deleteMembersByRoomIds(@Param("roomIds") List<Long> roomIds);

    int deleteRoomsByIds(@Param("roomIds") List<Long> roomIds);
}
