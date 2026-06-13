package com.cinoo.matchmateserver.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.chat.model.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 查询用户参与的所有会话，按最后消息时间降序。
     */
    List<Conversation> selectByUserId(Long userId);

    /**
     * 查询两个用户之间的会话。
     */
    Conversation selectByUserIds(Long userId1, Long userId2);
}
