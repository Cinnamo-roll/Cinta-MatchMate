package com.cinoo.matchmateserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cinoo.matchmateserver.model.domain.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 查询会话的消息列表，按创建时间升序。
     */
    List<Message> selectByConversationId(@Param("conversationId") Long conversationId,
                                         @Param("offset") long offset,
                                         @Param("limit") long limit);

    /**
     * 获取会话中未读消息数量。
     */
    long countUnread(@Param("conversationId") Long conversationId,
                     @Param("receiverId") Long receiverId);

    /**
     * 将会话中发送给指定用户的消息标记为已读。
     */
    int markAsRead(@Param("conversationId") Long conversationId,
                   @Param("receiverId") Long receiverId);

    List<Long> selectConversationIdsBefore(@Param("cutoff") Date cutoff);

    int deleteBefore(@Param("cutoff") Date cutoff);

    Message selectLatestByConversationId(@Param("conversationId") Long conversationId);
}
