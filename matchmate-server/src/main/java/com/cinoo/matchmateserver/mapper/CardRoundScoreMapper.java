package com.cinoo.matchmateserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.model.domain.CardRoundScore;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 牌局分数 Mapper。
 */
public interface CardRoundScoreMapper extends BaseMapper<CardRoundScore> {

    /**
     * 查询某局所有分数。
     */
    List<CardRoundScore> selectByRoundId(@Param("roundId") Long roundId);

    /**
     * 批量查询多局的积分明细。
     */
    List<CardRoundScore> selectByRoundIds(@Param("roundIds") List<Long> roundIds);

    /**
     * 批量插入。
     */
    int insertBatch(@Param("list") List<CardRoundScore> scores);
}
