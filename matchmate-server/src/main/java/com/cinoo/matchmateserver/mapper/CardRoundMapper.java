package com.cinoo.matchmateserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.model.domain.CardRound;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 牌局 Mapper。
 */
public interface CardRoundMapper extends BaseMapper<CardRound> {

    /**
     * 查询房间最大 roundNo。
     */
    Integer selectMaxRoundNo(@Param("roomId") Long roomId);

    /**
     * 查询房间牌局列表（按时间倒序）。
     */
    List<CardRound> selectByRoomId(@Param("roomId") Long roomId, @Param("limit") int limit);
}
