-- Shared fund records. Amount is stored in fen.
CREATE TABLE IF NOT EXISTS card_fund_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    type TINYINT NOT NULL COMMENT '1-creator paid first; 2-legacy reverse direction',
    amount INT NOT NULL COMMENT 'amount in fen',
    creator_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room_id (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='shared fund record';

-- Users selected to share a fund record.
CREATE TABLE IF NOT EXISTS card_fund_participant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    INDEX idx_fund_id (fund_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='shared fund participant';

-- Undo requests for transfer and fund records.
CREATE TABLE IF NOT EXISTS card_undo_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    target_type TINYINT NOT NULL COMMENT '1-transfer record 2-fund record',
    target_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0-pending 1-undone',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    done_time DATETIME NULL,
    UNIQUE KEY uk_pending_target (room_id, target_type, target_id, status),
    INDEX idx_room_status (room_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='card ledger undo request';

-- Approval records for undo requests.
CREATE TABLE IF NOT EXISTS card_undo_approval (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_request_user (request_id, user_id),
    INDEX idx_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='card ledger undo approval';

-- Lightweight application settings.
CREATE TABLE IF NOT EXISTS app_setting (
    setting_key VARCHAR(64) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='application setting';

INSERT INTO app_setting (setting_key, setting_value)
VALUES ('registration.daily.limit', '20')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
