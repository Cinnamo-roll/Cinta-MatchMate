package com.cinoo.matchmateserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.model.domain.CardFundRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 平摊资金 Mapper。
 */
public interface CardFundRecordMapper extends BaseMapper<CardFundRecord> {

    List<CardFundRecord> selectByRoomId(@Param("roomId") Long roomId, @Param("limit") int limit);
}
