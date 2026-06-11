package com.cinta.matchmateserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinta.matchmateserver.model.domain.UserTag;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserTagMapper extends BaseMapper<UserTag> {

    int insertBatch(@Param("userId") long userId, @Param("tagIds") List<Long> tagIds);
}
