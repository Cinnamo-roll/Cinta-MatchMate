package com.cinoo.matchmateserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.model.domain.CardExpense;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 费用 Mapper。
 */
public interface CardExpenseMapper extends BaseMapper<CardExpense> {

    /**
     * 查询房间费用列表。
     */
    List<CardExpense> selectByRoomId(@Param("roomId") Long roomId, @Param("limit") int limit);
}
