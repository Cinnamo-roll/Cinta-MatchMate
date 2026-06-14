package com.cinoo.matchmateserver.chat.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class ConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long targetUserId;

    private String targetUsername;

    private String targetAvatarUrl;

    private String lastMessage;

    private Date lastMessageTime;

    private Long lastMessageSenderId;

    private Long lastMessageReceiverId;

    private Integer lastMessageStatus;

    private Long unreadCount;

    private Boolean isOnline;

    private Date lastOnlineTime;
}
