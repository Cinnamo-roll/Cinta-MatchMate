CREATE DATABASE IF NOT EXISTS matchmate
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE matchmate;

CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) DEFAULT NULL,
  `userAccount` VARCHAR(64) NOT NULL,
  `avatarUrl` VARCHAR(512) DEFAULT NULL,
  `gender` INT DEFAULT NULL,
  `userPassword` VARCHAR(256) NOT NULL,
  `phone` VARCHAR(32) DEFAULT NULL,
  `email` VARCHAR(128) DEFAULT NULL,
  `userStatus` INT DEFAULT 0,
  `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updateTime` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `isDelete` INT DEFAULT 0,
  `userRole` INT DEFAULT 0,
  `totalScore` INT DEFAULT 0,
  `wins` INT DEFAULT 0,
  `losses` INT DEFAULT 0,
  `winRate` DECIMAL(6,4) DEFAULT 0.0000,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_userAccount` (`userAccount`),
  KEY `idx_user_status` (`isDelete`, `userStatus`),
  KEY `idx_user_create_time` (`createTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tagName` VARCHAR(64) NOT NULL,
  `category` VARCHAR(64) DEFAULT NULL,
  `sortOrder` INT DEFAULT 0,
  `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updateTime` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `isDelete` INT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name_category` (`tagName`, `category`),
  KEY `idx_tag_category` (`category`, `sortOrder`),
  KEY `idx_tag_delete` (`isDelete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `userId` BIGINT NOT NULL,
  `tagId` BIGINT NOT NULL,
  `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tag` (`userId`, `tagId`),
  KEY `idx_user_tag_tag` (`tagId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `conversation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `userId1` BIGINT NOT NULL,
  `userId2` BIGINT NOT NULL,
  `lastMessage` VARCHAR(1024) DEFAULT NULL,
  `lastMessageTime` DATETIME DEFAULT NULL,
  `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updateTime` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `isDelete` INT DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_conversation_user1` (`userId1`),
  KEY `idx_conversation_user2` (`userId2`),
  KEY `idx_conversation_last_time` (`lastMessageTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `conversationId` BIGINT NOT NULL,
  `senderId` BIGINT NOT NULL,
  `receiverId` BIGINT NOT NULL,
  `content` VARCHAR(2048) NOT NULL,
  `messageType` INT DEFAULT 0,
  `status` INT DEFAULT 0,
  `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updateTime` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `isDelete` INT DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_message_conversation` (`conversationId`),
  KEY `idx_message_receiver_status` (`receiverId`, `status`),
  KEY `idx_message_create_time` (`createTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cardRoom` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `roomCode` VARCHAR(16) NOT NULL,
  `ownerId` BIGINT NOT NULL,
  `status` INT DEFAULT 0,
  `maxMembers` INT DEFAULT 4,
  `settleTime` DATETIME DEFAULT NULL,
  `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updateTime` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `isDelete` INT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_room_code` (`roomCode`),
  KEY `idx_card_room_owner` (`ownerId`),
  KEY `idx_card_room_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cardRoomMember` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `roomId` BIGINT NOT NULL,
  `userId` BIGINT NOT NULL,
  `status` INT DEFAULT 0,
  `totalScore` INT DEFAULT 0,
  `settleScore` INT DEFAULT 0,
  `wins` INT DEFAULT 0,
  `losses` INT DEFAULT 0,
  `joinTime` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `leaveTime` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_room_member` (`roomId`, `userId`),
  KEY `idx_card_room_member_user` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cardRound` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `roomId` BIGINT NOT NULL,
  `roundNo` INT NOT NULL,
  `settled` INT DEFAULT 1,
  `creatorId` BIGINT DEFAULT NULL,
  `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_round_no` (`roomId`, `roundNo`),
  KEY `idx_card_round_creator` (`creatorId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cardRoundScore` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `roundId` BIGINT NOT NULL,
  `userId` BIGINT NOT NULL,
  `score` INT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_card_round_score_round` (`roundId`),
  KEY `idx_card_round_score_user` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `card_fund_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `room_id` BIGINT NOT NULL,
  `type` INT NOT NULL,
  `amount` INT NOT NULL,
  `creator_id` BIGINT NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_card_fund_room` (`room_id`),
  KEY `idx_card_fund_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `card_fund_participant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `fund_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_card_fund_participant_fund` (`fund_id`),
  KEY `idx_card_fund_participant_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `card_undo_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `room_id` BIGINT NOT NULL,
  `target_type` INT NOT NULL,
  `target_id` BIGINT NOT NULL,
  `requester_id` BIGINT NOT NULL,
  `status` INT DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `done_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_card_undo_room` (`room_id`),
  KEY `idx_card_undo_target` (`target_type`, `target_id`),
  KEY `idx_card_undo_requester` (`requester_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `card_undo_approval` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `request_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_undo_approval` (`request_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `app_setting` (
  `setting_key` VARCHAR(128) NOT NULL,
  `setting_value` VARCHAR(512) NOT NULL,
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tag (`tagName`, `category`, `sortOrder`) VALUES
('游戏', '兴趣爱好', 1),
('电影', '兴趣爱好', 2),
('音乐', '兴趣爱好', 3),
('运动', '兴趣爱好', 4),
('旅游', '兴趣爱好', 5),
('美食', '兴趣爱好', 6),
('读书', '兴趣爱好', 7),
('摄影', '兴趣爱好', 8),
('开朗', '性格特点', 1),
('内向', '性格特点', 2),
('幽默', '性格特点', 3),
('细心', '性格特点', 4),
('乐观', '性格特点', 5),
('独立', '性格特点', 6),
('早起', '生活方式', 1),
('夜猫子', '生活方式', 2),
('健身', '生活方式', 3),
('宅家', '生活方式', 4),
('养宠物', '生活方式', 5),
('线下见面', '社交偏好', 1),
('线上聊天', '社交偏好', 2),
('AA制', '社交偏好', 3),
('小组活动', '社交偏好', 4),
('麻将', '牌类偏好', 1),
('扑克', '牌类偏好', 2),
('斗地主', '牌类偏好', 3),
('桥牌', '牌类偏好', 4),
('UNO', '牌类偏好', 5)
ON DUPLICATE KEY UPDATE
  `category` = VALUES(`category`),
  `sortOrder` = VALUES(`sortOrder`);

INSERT INTO app_setting (`setting_key`, `setting_value`) VALUES
('registration.daily.limit', '20')
ON DUPLICATE KEY UPDATE
  `setting_value` = VALUES(`setting_value`);
