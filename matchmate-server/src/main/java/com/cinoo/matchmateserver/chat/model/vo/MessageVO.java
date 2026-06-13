package com.cinoo.matchmateserver.chat.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class MessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long conversationId;

    private Long senderId;

    private Long receiverId;

    private String content;

    private Integer messageType;

    private Integer status;

    private Date createTime;
}
