-- 平摊资金记录表
CREATE TABLE IF NOT EXISTS card_fund_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    type TINYINT NOT NULL COMMENT '1-加钱 2-扣钱',
    amount INT NOT NULL COMMENT '金额（分）',
    creator_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room_id (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平摊资金记录';

-- 平摊资金参与明细表
CREATE TABLE IF NOT EXISTS card_fund_participant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    INDEX idx_fund_id (fund_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平摊资金参与明细';
