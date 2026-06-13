package com.cinoo.matchmateserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.model.domain.CardUndoRequest;
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
