package com.cinoo.matchmateserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.model.domain.CardUndoApproval;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CardUndoApprovalMapper extends BaseMapper<CardUndoApproval> {

    List<CardUndoApproval> selectByRequestIds(@Param("requestIds") List<Long> requestIds);

    int countByRequestId(@Param("requestId") Long requestId);

    int insertIgnore(CardUndoApproval approval);
}
