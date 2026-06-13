-- MatchMate card ledger schema reference.
-- Amount fields are stored in fen.

CREATE TABLE cardRoom (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT 'room id',
    roomCode      CHAR(6)      NOT NULL COMMENT 'six digit room code',
    ownerId       BIGINT       NOT NULL COMMENT 'owner user id',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0-active 1-ended',
    maxMembers    TINYINT      NOT NULL DEFAULT 8 COMMENT 'max member count',
    settleTime    DATETIME     DEFAULT NULL COMMENT 'settle time',
    createTime    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    updateTime    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    isDelete      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-normal 1-deleted',
    UNIQUE KEY uk_roomCode (roomCode),
    INDEX idx_ownerId (ownerId),
    INDEX idx_status (status),
    INDEX idx_retention (status, isDelete, createTime, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='card ledger room';

CREATE TABLE cardRoomMember (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT 'id',
    roomId        BIGINT       NOT NULL COMMENT 'room id',
    userId        BIGINT       NOT NULL COMMENT 'user id',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0-in room 1-left 2-settled',
    totalScore    INT          NOT NULL DEFAULT 0 COMMENT 'current amount in fen',
    settleScore   INT          DEFAULT NULL COMMENT 'frozen amount in fen when settled',
    wins          INT          NOT NULL DEFAULT 0 COMMENT 'win count in room',
    losses        INT          NOT NULL DEFAULT 0 COMMENT 'loss count in room',
    joinTime      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'join time',
    leaveTime     DATETIME     DEFAULT NULL COMMENT 'leave or settle time',
    UNIQUE KEY uk_room_user (roomId, userId),
    INDEX idx_userId (userId),
    INDEX idx_room_status (roomId, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='card ledger room member';

CREATE TABLE cardRound (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT 'round id',
    roomId        BIGINT       NOT NULL COMMENT 'room id',
    roundNo       INT          NOT NULL COMMENT 'legacy sequence number',
    settled       TINYINT      NOT NULL DEFAULT 0 COMMENT '0-not settled 1-settled',
    creatorId     BIGINT       NOT NULL COMMENT 'creator user id',
    createTime    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    UNIQUE KEY uk_room_round (roomId, roundNo),
    INDEX idx_roomId (roomId, createTime DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='card ledger transfer record';

CREATE TABLE cardRoundScore (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT 'id',
    roundId       BIGINT       NOT NULL COMMENT 'round id',
    userId        BIGINT       NOT NULL COMMENT 'user id',
    score         INT          NOT NULL COMMENT 'amount in fen, positive means income',
    UNIQUE KEY uk_round_user (roundId, userId),
    INDEX idx_roundId (roundId),
    INDEX idx_userId (userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='card ledger transfer detail';

CREATE TABLE card_fund_record (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT 'fund record id',
    room_id       BIGINT       NOT NULL COMMENT 'room id',
    type          TINYINT      NOT NULL COMMENT '1-creator paid first',
    amount        INT          NOT NULL COMMENT 'amount in fen',
    creator_id    BIGINT       NOT NULL COMMENT 'creator user id',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    INDEX idx_room_id (room_id, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='shared fund record';

CREATE TABLE card_fund_participant (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT 'id',
    fund_id       BIGINT       NOT NULL COMMENT 'fund record id',
    user_id       BIGINT       NOT NULL COMMENT 'participant user id',
    UNIQUE KEY uk_fund_user (fund_id, user_id),
    INDEX idx_fund_id (fund_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='shared fund participant';

CREATE TABLE card_undo_request (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT 'undo request id',
    room_id       BIGINT       NOT NULL COMMENT 'room id',
    target_type   TINYINT      NOT NULL COMMENT '1-transfer record 2-fund record',
    target_id     BIGINT       NOT NULL COMMENT 'target id',
    requester_id  BIGINT       NOT NULL COMMENT 'requester user id',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0-pending 1-undone',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    done_time     DATETIME     DEFAULT NULL COMMENT 'done time',
    UNIQUE KEY uk_pending_target (room_id, target_type, target_id, status),
    INDEX idx_room_status (room_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='card ledger undo request';

CREATE TABLE card_undo_approval (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT 'approval id',
    request_id    BIGINT       NOT NULL COMMENT 'undo request id',
    user_id       BIGINT       NOT NULL COMMENT 'approver user id',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    UNIQUE KEY uk_request_user (request_id, user_id),
    INDEX idx_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='card ledger undo approval';

ALTER TABLE `user`
  ADD COLUMN totalScore INT NOT NULL DEFAULT 0 COMMENT 'total amount in fen',
  ADD COLUMN wins       INT NOT NULL DEFAULT 0 COMMENT 'total wins',
  ADD COLUMN losses     INT NOT NULL DEFAULT 0 COMMENT 'total losses',
  ADD COLUMN winRate    DECIMAL(5,4) NOT NULL DEFAULT 0.0000 COMMENT 'win rate';
