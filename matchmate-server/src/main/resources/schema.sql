-- ============================================================
-- MatchMate 数据库初始化脚本
-- 注意：mybatis-plus.map-underscore-to-camel-case: false
-- 列名与 Java 实体字段名完全一致（除了 @TableField 指定的）
-- ============================================================

CREATE DATABASE IF NOT EXISTS matchmate
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE matchmate;

-- ----------------------------
-- 用户表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `userAccount` VARCHAR(64)  NOT NULL COMMENT '登录账号',
    `avatarUrl`   VARCHAR(512) DEFAULT NULL COMMENT '头像地址',
    `gender`      INT          DEFAULT NULL COMMENT '性别',
    `userPassword` VARCHAR(256) NOT NULL COMMENT '加密密码',
    `phone`       VARCHAR(32)  DEFAULT NULL COMMENT '手机号',
    `email`       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `userStatus`  INT          DEFAULT 0 COMMENT '状态 0=正常',
    `createTime`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`    INT          DEFAULT 0 COMMENT '逻辑删除 0=未删除 1=已删除',
    `userRole`    INT          DEFAULT 0 COMMENT '角色 0=普通用户 1=管理员',
    `totalScore`  INT          DEFAULT 0 COMMENT '赢得金额（分）',
    `wins`        INT          DEFAULT 0 COMMENT '累计胜局',
    `losses`      INT          DEFAULT 0 COMMENT '累计负局',
    `winRate`     DECIMAL(6,4) DEFAULT 0.0000 COMMENT '胜率',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_userAccount` (`userAccount`),
    KEY `idx_isDelete_userStatus` (`isDelete`, `userStatus`),
    KEY `idx_createTime` (`createTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ----------------------------
-- 标签表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tag` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `tagName`    VARCHAR(64) NOT NULL COMMENT '标签名',
    `category`   VARCHAR(64) DEFAULT NULL COMMENT '分类',
    `sortOrder`  INT         DEFAULT 0 COMMENT '排序',
    `createTime` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`   INT         DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_isDelete` (`isDelete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

-- ----------------------------
-- 用户-标签关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `user_tag` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `userId`     BIGINT   NOT NULL COMMENT '用户ID',
    `tagId`      BIGINT   NOT NULL COMMENT '标签ID',
    `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_userId` (`userId`),
    KEY `idx_tagId` (`tagId`),
    UNIQUE KEY `uk_userId_tagId` (`userId`, `tagId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户标签关联表';

-- ----------------------------
-- 会话表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `conversation` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `userId1`         BIGINT        NOT NULL COMMENT '用户1',
    `userId2`         BIGINT        NOT NULL COMMENT '用户2',
    `lastMessage`     VARCHAR(1024) DEFAULT NULL COMMENT '最后一条消息',
    `lastMessageTime` DATETIME      DEFAULT NULL COMMENT '最后消息时间',
    `createTime`      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`        INT           DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_userId1` (`userId1`),
    KEY `idx_userId2` (`userId2`),
    KEY `idx_lastMessageTime` (`lastMessageTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- ----------------------------
-- 消息表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `message` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `conversationId` BIGINT        NOT NULL COMMENT '会话ID',
    `senderId`       BIGINT        NOT NULL COMMENT '发送者',
    `receiverId`     BIGINT        NOT NULL COMMENT '接收者',
    `content`        TEXT          DEFAULT NULL COMMENT '消息内容',
    `messageType`    INT           DEFAULT 0 COMMENT '消息类型',
    `status`         INT           DEFAULT 0 COMMENT '状态 0=未读 1=已读',
    `createTime`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`       INT           DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_conversationId` (`conversationId`),
    KEY `idx_receiverId_status` (`receiverId`, `status`),
    KEY `idx_createTime` (`createTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- ----------------------------
-- 牌局房间表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `cardRoom` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `roomCode`   VARCHAR(16) NOT NULL COMMENT '房间码',
    `ownerId`    BIGINT      NOT NULL COMMENT '房主',
    `status`     INT         DEFAULT 0 COMMENT '状态 0=进行中 1=已结束',
    `maxMembers` INT         DEFAULT 6 COMMENT '最大人数',
    `settleTime` DATETIME    DEFAULT NULL COMMENT '结算时间',
    `createTime` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`   INT         DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_roomCode` (`roomCode`),
    KEY `idx_ownerId` (`ownerId`),
    KEY `idx_status_isDelete` (`status`, `isDelete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='牌局房间表';

-- ----------------------------
-- 房间成员表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `cardRoomMember` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `roomId`      BIGINT   NOT NULL COMMENT '房间ID',
    `userId`      BIGINT   NOT NULL COMMENT '用户ID',
    `status`      INT      DEFAULT 0 COMMENT '状态 0=活跃 1=已离开',
    `totalScore`  INT      DEFAULT 0 COMMENT '累计分',
    `settleScore` INT      DEFAULT 0 COMMENT '结算分',
    `wins`        INT      DEFAULT 0 COMMENT '胜局',
    `losses`      INT      DEFAULT 0 COMMENT '负局',
    `joinTime`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `leaveTime`   DATETIME DEFAULT NULL COMMENT '离开时间',
    PRIMARY KEY (`id`),
    KEY `idx_roomId` (`roomId`),
    KEY `idx_userId_status` (`userId`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='房间成员表';

-- ----------------------------
-- 牌局记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `cardRound` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `roomId`     BIGINT   NOT NULL COMMENT '房间ID',
    `roundNo`    INT      NOT NULL COMMENT '局号',
    `settled`    INT      DEFAULT 0 COMMENT '是否已结算',
    `creatorId`  BIGINT   DEFAULT NULL COMMENT '创建人',
    `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_roomId` (`roomId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='牌局记录表';

-- ----------------------------
-- 牌局分数表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `cardRoundScore` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT,
    `roundId` BIGINT NOT NULL COMMENT '牌局ID',
    `userId`  BIGINT NOT NULL COMMENT '用户ID',
    `score`   INT    DEFAULT 0 COMMENT '分数',
    PRIMARY KEY (`id`),
    KEY `idx_roundId` (`roundId`),
    KEY `idx_userId` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='牌局分数表';

-- ----------------------------
-- 资金记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `card_fund_record` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `room_id`     BIGINT   NOT NULL COMMENT '房间ID',
    `type`        INT      NOT NULL COMMENT '类型 0=加注 1=扣减',
    `amount`      INT      NOT NULL COMMENT '金额（分）',
    `creator_id`  BIGINT   NOT NULL COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_room_id` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资金记录表';

-- ----------------------------
-- 资金分摊明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `card_fund_participant` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT,
    `fund_id` BIGINT NOT NULL COMMENT '资金记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    PRIMARY KEY (`id`),
    KEY `idx_fund_id` (`fund_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资金分摊明细表';

-- ----------------------------
-- 撤销请求表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `card_undo_request` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT,
    `room_id`      BIGINT   NOT NULL COMMENT '房间ID',
    `target_type`  INT      NOT NULL COMMENT '撤销目标类型',
    `target_id`    BIGINT   NOT NULL COMMENT '撤销目标ID',
    `requester_id` BIGINT   NOT NULL COMMENT '请求人',
    `status`       INT      DEFAULT 0 COMMENT '状态 0=待审批 1=通过 2=拒绝',
    `create_time`  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `done_time`    DATETIME DEFAULT NULL COMMENT '处理时间',
    PRIMARY KEY (`id`),
    KEY `idx_room_id` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='撤销请求表';

-- ----------------------------
-- 撤销审批表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `card_undo_approval` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `request_id`  BIGINT   NOT NULL COMMENT '撤销请求ID',
    `user_id`     BIGINT   NOT NULL COMMENT '审批人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='撤销审批表';

-- ----------------------------
-- 应用设置表（UserMapper.xml 引用，非实体类）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `app_setting` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `setting_key`  VARCHAR(128) NOT NULL COMMENT '配置键',
    `setting_value` TEXT        DEFAULT NULL COMMENT '配置值',
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_setting_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用设置表';
