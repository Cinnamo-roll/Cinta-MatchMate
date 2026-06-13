package com.cinoo.matchmateserver.tag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.tag.model.entity.Tag;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TagMapper extends BaseMapper<Tag> {

    List<String> selectTagNamesByUserId(@Param("userId") long userId);
}
