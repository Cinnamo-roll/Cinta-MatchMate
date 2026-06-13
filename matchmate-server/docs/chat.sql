-- 会话表
CREATE TABLE conversation (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '会话 ID',
    userId1       BIGINT       NOT NULL COMMENT '发起方用户 ID',
    userId2       BIGINT       NOT NULL COMMENT '接收方用户 ID',
    lastMessage   VARCHAR(2000) DEFAULT NULL COMMENT '最后一条消息内容',
    lastMessageTime DATETIME   DEFAULT NULL COMMENT '最后消息时间',
    createTime    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete      TINYINT      DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    INDEX idx_userIds (userId1, userId2),
    INDEX idx_userId1 (userId1, lastMessageTime DESC),
    INDEX idx_userId2 (userId2, lastMessageTime DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- 消息表
CREATE TABLE message (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '消息 ID',
    conversationId  BIGINT       NOT NULL COMMENT '会话 ID',
    senderId        BIGINT       NOT NULL COMMENT '发送方用户 ID',
    receiverId      BIGINT       NOT NULL COMMENT '接收方用户 ID',
    content         TEXT         NOT NULL COMMENT '消息内容',
    messageType     TINYINT      DEFAULT 0 COMMENT '消息类型：0-文本',
    status          TINYINT      DEFAULT 0 COMMENT '状态：0-未读 1-已读',
    createTime      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete        TINYINT      DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    INDEX idx_conversationId (conversationId, createTime DESC),
    INDEX idx_receiverUnread (conversationId, receiverId, status),
    INDEX idx_message_create_time (createTime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';
