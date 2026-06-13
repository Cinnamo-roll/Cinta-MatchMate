package com.cinoo.matchmateserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.model.domain.CardFundParticipant;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 平摊资金参与 Mapper。
 */
public interface CardFundParticipantMapper extends BaseMapper<CardFundParticipant> {

    List<CardFundParticipant> selectByFundIds(@Param("fundIds") List<Long> fundIds);

    List<CardFundParticipant> selectByFundId(@Param("fundId") Long fundId);

    int insertBatch(@Param("list") List<CardFundParticipant> participants);

    int deleteByFundId(@Param("fundId") Long fundId);
}
