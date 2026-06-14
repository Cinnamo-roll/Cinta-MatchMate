package com.cinoo.matchmateserver.user.mapper;

import com.cinoo.matchmateserver.user.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 用户数据访问接口。
 */
public interface UserMapper extends BaseMapper<User> {

    Page<User> searchPageByKeywordAndTags(
            Page<User> page,
            @Param("keyword") String keyword,
            @Param("tagList") List<String> tagList,
            @Param("tagCount") int tagCount,
            @Param("currentUserId") Long currentUserId,
            @Param("adminRole") int adminRole
    );

    List<User> selectRecommendationCandidates(
            @Param("excludedUserId") Long excludedUserId,
            @Param("limit") int limit
    );

    Integer selectAppSettingInt(@Param("settingKey") String settingKey);

    int upsertAppSetting(
            @Param("settingKey") String settingKey,
            @Param("settingValue") String settingValue
    );

    Long countRegistrationsByStatusAndTimeRange(
            @Param("userStatus") int userStatus,
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime
    );

    Long countUsersByStatus(@Param("userStatus") int userStatus);

    Page<User> selectUsersByStatus(
            Page<User> page,
            @Param("userStatus") int userStatus
    );

    /**
     * 查询全体用户排名。
     */
    List<User> selectCardRanking(
            @Param("userId") Long userId,
            @Param("limit") int limit);

    /**
     * 原子累加用户统计。
     */
    int addStats(@Param("userId") Long userId,
                 @Param("totalScore") int totalScore,
                 @Param("wins") int wins,
                 @Param("losses") int losses);
}
