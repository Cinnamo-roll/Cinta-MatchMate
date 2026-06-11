package com.cinta.matchmateserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinta.matchmateserver.model.domain.Tag;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TagMapper extends BaseMapper<Tag> {

    List<String> selectTagNamesByUserId(@Param("userId") long userId);
}
