package com.cinoo.matchmateserver.tag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.tag.model.entity.UserTag;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserTagMapper extends BaseMapper<UserTag> {

    int insertBatch(@Param("userId") long userId, @Param("tagIds") List<Long> tagIds);
}
