package com.cinoo.matchmateserver.card.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.card.model.entity.CardUndoRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CardUndoRequestMapper extends BaseMapper<CardUndoRequest> {

    CardUndoRequest selectPending(
            @Param("roomId") Long roomId,
            @Param("targetType") Integer targetType,
            @Param("targetId") Long targetId);

    List<CardUndoRequest> selectPendingByRoomId(@Param("roomId") Long roomId);

    int markDone(@Param("requestId") Long requestId);
}
