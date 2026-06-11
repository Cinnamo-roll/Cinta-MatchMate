package com.cinta.matchmateserver.mapper;

import com.cinta.matchmateserver.model.domain.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户数据访问接口。
 */
public interface UserMapper extends BaseMapper<User> {

    List<User> searchByKeywordAndTags(
            @Param("keyword") String keyword,
            @Param("tagList") List<String> tagList,
            @Param("tagCount") int tagCount
    );

    List<User> recommendUsers(@Param("limit") int limit);
}
