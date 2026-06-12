-- ============================================
-- 打牌记账本 - 多人房间功能数据库迁移
-- ============================================

-- 房间表
CREATE TABLE cardRoom (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '房间ID',
    roomCode      CHAR(6)      NOT NULL COMMENT '6位数字房号',
    ownerId       BIGINT       NOT NULL COMMENT '房主用户ID',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0-进行中 1-已结束',
    maxMembers    TINYINT      NOT NULL DEFAULT 8 COMMENT '最大成员数',
    teaAmount     INT          NOT NULL DEFAULT 0 COMMENT '茶钱总额(分)',
    mealAmount    INT          NOT NULL DEFAULT 0 COMMENT '饭钱总额(分)',
    settleTime    DATETIME     DEFAULT NULL COMMENT '结算时间',
    createTime    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    UNIQUE KEY uk_roomCode (roomCode),
    INDEX idx_ownerId (ownerId),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打牌记账房间';

-- 房间成员表
CREATE TABLE cardRoomMember (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    roomId        BIGINT       NOT NULL COMMENT '房间ID',
    userId        BIGINT       NOT NULL COMMENT '用户ID',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0-在房间 1-已退出 2-已结算',
    totalScore    INT          NOT NULL DEFAULT 0 COMMENT '当前总积分(牌局+费用分摊)',
    settleScore   INT          DEFAULT NULL COMMENT '退出/结算时冻结的最终积分',
    wins          INT          NOT NULL DEFAULT 0 COMMENT '房间内胜局数',
    losses        INT          NOT NULL DEFAULT 0 COMMENT '房间内负局数',
    joinTime      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    leaveTime     DATETIME     DEFAULT NULL COMMENT '退出/结算时间',
    UNIQUE KEY uk_room_user (roomId, userId),
    INDEX idx_userId (userId),
    INDEX idx_room_status (roomId, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房间成员';

-- 牌局表
CREATE TABLE cardRound (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '牌局ID',
    roomId        BIGINT       NOT NULL COMMENT '房间ID',
    roundNo       INT          NOT NULL COMMENT '局号(房间内递增)',
    settled       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已结算: 0-未结算 1-已结算',
    creatorId     BIGINT       NOT NULL COMMENT '记录人ID',
    createTime    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_room_round (roomId, roundNo),
    INDEX idx_roomId (roomId, createTime DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='牌局记录';

-- 牌局分数表
CREATE TABLE cardRoundScore (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    roundId       BIGINT       NOT NULL COMMENT '牌局ID',
    userId        BIGINT       NOT NULL COMMENT '用户ID',
    score         INT          NOT NULL COMMENT '本局积分(可正可负)',
    UNIQUE KEY uk_round_user (roundId, userId),
    INDEX idx_roundId (roundId),
    INDEX idx_userId (userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='牌局分数';

-- 费用表（茶/饭）
CREATE TABLE cardExpense (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '费用ID',
    roomId        BIGINT       NOT NULL COMMENT '房间ID',
    type          TINYINT      NOT NULL COMMENT '类型: 1-茶钱 2-饭钱',
    amount        INT          NOT NULL COMMENT '金额(分)',
    payerId       BIGINT       NOT NULL COMMENT '支付人ID',
    createTime    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_roomId (roomId, createTime DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用记录';

-- 费用分摊表
CREATE TABLE cardExpenseParticipant (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    expenseId     BIGINT       NOT NULL COMMENT '费用ID',
    userId        BIGINT       NOT NULL COMMENT '分摊用户ID',
    UNIQUE KEY uk_expense_user (expenseId, userId),
    INDEX idx_userId (userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用分摊明细';

-- 用户表新增统计字段
ALTER TABLE `user`
  ADD COLUMN totalScore INT NOT NULL DEFAULT 0 COMMENT '累计积分',
  ADD COLUMN wins       INT NOT NULL DEFAULT 0 COMMENT '累计胜局',
  ADD COLUMN losses     INT NOT NULL DEFAULT 0 COMMENT '累计负局',
  ADD COLUMN winRate    DECIMAL(5,4) NOT NULL DEFAULT 0.0000 COMMENT '胜率';
