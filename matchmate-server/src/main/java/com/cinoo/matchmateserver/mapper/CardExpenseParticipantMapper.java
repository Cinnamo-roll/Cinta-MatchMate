package com.cinoo.matchmateserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.model.domain.CardExpenseParticipant;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 费用分摊 Mapper。
 */
public interface CardExpenseParticipantMapper extends BaseMapper<CardExpenseParticipant> {

    /**
     * 查询某费用分摊列表。
     */
    List<CardExpenseParticipant> selectByExpenseId(@Param("expenseId") Long expenseId);

    /**
     * 批量查询多笔费用的分摊成员。
     */
    List<CardExpenseParticipant> selectByExpenseIds(@Param("expenseIds") List<Long> expenseIds);

    /**
     * 批量插入。
     */
    int insertBatch(@Param("list") List<CardExpenseParticipant> participants);
}
