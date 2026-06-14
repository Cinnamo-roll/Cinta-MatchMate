-- MySQL dump 10.13  Distrib 8.3.0, for Win64 (x86_64)
--
-- Host: localhost    Database: matchmate
-- ------------------------------------------------------
-- Server version	8.3.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `matchmate`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `matchmate` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `matchmate`;

--
-- Table structure for table `app_setting`
--

DROP TABLE IF EXISTS `app_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_setting` (
  `setting_key` varchar(64) NOT NULL,
  `setting_value` varchar(255) NOT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='application setting';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app_setting`
--

LOCK TABLES `app_setting` WRITE;
/*!40000 ALTER TABLE `app_setting` DISABLE KEYS */;
INSERT INTO `app_setting` VALUES ('registration.daily.limit','0','2026-06-14 16:07:58');
/*!40000 ALTER TABLE `app_setting` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `card_fund_participant`
--

DROP TABLE IF EXISTS `card_fund_participant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `card_fund_participant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `fund_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_fund_id` (`fund_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平摊资金参与明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `card_fund_participant`
--

LOCK TABLES `card_fund_participant` WRITE;
/*!40000 ALTER TABLE `card_fund_participant` DISABLE KEYS */;
/*!40000 ALTER TABLE `card_fund_participant` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `card_fund_record`
--

DROP TABLE IF EXISTS `card_fund_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `card_fund_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `room_id` bigint NOT NULL,
  `type` tinyint NOT NULL COMMENT '1-加钱 2-扣钱',
  `amount` int NOT NULL COMMENT '金额（分）',
  `creator_id` bigint NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_room_id` (`room_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平摊资金记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `card_fund_record`
--

LOCK TABLES `card_fund_record` WRITE;
/*!40000 ALTER TABLE `card_fund_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `card_fund_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `card_undo_approval`
--

DROP TABLE IF EXISTS `card_undo_approval`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `card_undo_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_user` (`request_id`,`user_id`),
  KEY `idx_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='牌局记录撤销同意';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `card_undo_approval`
--

LOCK TABLES `card_undo_approval` WRITE;
/*!40000 ALTER TABLE `card_undo_approval` DISABLE KEYS */;
/*!40000 ALTER TABLE `card_undo_approval` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `card_undo_request`
--

DROP TABLE IF EXISTS `card_undo_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `card_undo_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `room_id` bigint NOT NULL,
  `target_type` tinyint NOT NULL COMMENT '1-牌局记录 2-资金记录',
  `target_id` bigint NOT NULL,
  `requester_id` bigint NOT NULL,
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0-待同意 1-已撤销',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `done_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pending_target` (`room_id`,`target_type`,`target_id`,`status`),
  KEY `idx_room_status` (`room_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='牌局记录撤销申请';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `card_undo_request`
--

LOCK TABLES `card_undo_request` WRITE;
/*!40000 ALTER TABLE `card_undo_request` DISABLE KEYS */;
/*!40000 ALTER TABLE `card_undo_request` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cardroom`
--

DROP TABLE IF EXISTS `cardroom`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cardroom` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '房间ID',
  `roomCode` char(6) NOT NULL COMMENT '6位数字房号',
  `ownerId` bigint NOT NULL COMMENT '房主用户ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0-进行中 1-已结束',
  `maxMembers` tinyint NOT NULL DEFAULT '8' COMMENT '最大成员数',
  `settleTime` datetime DEFAULT NULL COMMENT '结算时间',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_roomCode` (`roomCode`),
  KEY `idx_ownerId` (`ownerId`),
  KEY `idx_status` (`status`),
  KEY `idx_retention` (`status`,`isDelete`,`createTime`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打牌记账房间';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cardroom`
--

LOCK TABLES `cardroom` WRITE;
/*!40000 ALTER TABLE `cardroom` DISABLE KEYS */;
/*!40000 ALTER TABLE `cardroom` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cardroommember`
--

DROP TABLE IF EXISTS `cardroommember`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cardroommember` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `roomId` bigint NOT NULL COMMENT '房间ID',
  `userId` bigint NOT NULL COMMENT '用户ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0-在房间 1-已退出 2-已结算',
  `totalScore` int NOT NULL DEFAULT '0' COMMENT '当前总积分(牌局+费用分摊)',
  `settleScore` int DEFAULT NULL COMMENT '退出/结算时冻结的最终积分',
  `wins` int NOT NULL DEFAULT '0' COMMENT '房间内胜局数',
  `losses` int NOT NULL DEFAULT '0' COMMENT '房间内负局数',
  `joinTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `leaveTime` datetime DEFAULT NULL COMMENT '退出/结算时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_user` (`roomId`,`userId`),
  KEY `idx_userId` (`userId`),
  KEY `idx_room_status` (`roomId`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房间成员';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cardroommember`
--

LOCK TABLES `cardroommember` WRITE;
/*!40000 ALTER TABLE `cardroommember` DISABLE KEYS */;
/*!40000 ALTER TABLE `cardroommember` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cardround`
--

DROP TABLE IF EXISTS `cardround`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cardround` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '牌局ID',
  `roomId` bigint NOT NULL COMMENT '房间ID',
  `roundNo` int NOT NULL COMMENT '局号(房间内递增)',
  `settled` tinyint NOT NULL DEFAULT '0' COMMENT '是否已结算: 0-未结算 1-已结算',
  `creatorId` bigint NOT NULL COMMENT '记录人ID',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_round` (`roomId`,`roundNo`),
  KEY `idx_roomId` (`roomId`,`createTime` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='牌局记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cardround`
--

LOCK TABLES `cardround` WRITE;
/*!40000 ALTER TABLE `cardround` DISABLE KEYS */;
/*!40000 ALTER TABLE `cardround` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cardroundscore`
--

DROP TABLE IF EXISTS `cardroundscore`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cardroundscore` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `roundId` bigint NOT NULL COMMENT '牌局ID',
  `userId` bigint NOT NULL COMMENT '用户ID',
  `score` int NOT NULL COMMENT '本局积分(可正可负)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_round_user` (`roundId`,`userId`),
  KEY `idx_roundId` (`roundId`),
  KEY `idx_userId` (`userId`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='牌局分数';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cardroundscore`
--

LOCK TABLES `cardroundscore` WRITE;
/*!40000 ALTER TABLE `cardroundscore` DISABLE KEYS */;
/*!40000 ALTER TABLE `cardroundscore` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `conversation`
--

DROP TABLE IF EXISTS `conversation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话 ID',
  `userId1` bigint NOT NULL COMMENT '发起方用户 ID',
  `userId2` bigint NOT NULL COMMENT '接收方用户 ID',
  `lastMessage` varchar(2000) DEFAULT NULL COMMENT '最后一条消息内容',
  `lastMessageTime` datetime DEFAULT NULL COMMENT '最后消息时间',
  `createTime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete` tinyint DEFAULT '0' COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_userIds` (`userId1`,`userId2`),
  KEY `idx_userId1` (`userId1`,`lastMessageTime` DESC),
  KEY `idx_userId2` (`userId2`,`lastMessageTime` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `conversation`
--

LOCK TABLES `conversation` WRITE;
/*!40000 ALTER TABLE `conversation` DISABLE KEYS */;
/*!40000 ALTER TABLE `conversation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `message`
--

DROP TABLE IF EXISTS `message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息 ID',
  `conversationId` bigint NOT NULL COMMENT '会话 ID',
  `senderId` bigint NOT NULL COMMENT '发送方用户 ID',
  `receiverId` bigint NOT NULL COMMENT '接收方用户 ID',
  `content` text NOT NULL COMMENT '消息内容',
  `messageType` tinyint DEFAULT '0' COMMENT '消息类型：0-文本',
  `status` tinyint DEFAULT '0' COMMENT '状态：0-未读 1-已读',
  `createTime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete` tinyint DEFAULT '0' COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_conversationId` (`conversationId`,`createTime` DESC),
  KEY `idx_receiverUnread` (`conversationId`,`receiverId`,`status`),
  KEY `idx_message_create_time` (`createTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `message`
--

LOCK TABLES `message` WRITE;
/*!40000 ALTER TABLE `message` DISABLE KEYS */;
/*!40000 ALTER TABLE `message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tag`
--

DROP TABLE IF EXISTS `tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `tagName` varchar(64) NOT NULL COMMENT '标签名称',
  `category` varchar(64) NOT NULL COMMENT '标签分类',
  `sortOrder` int NOT NULL DEFAULT '0' COMMENT '分类内排序',
  `createTime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`tagName`),
  KEY `idx_tag_category_sort` (`category`,`sortOrder`)
) ENGINE=InnoDB AUTO_INCREMENT=147 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tag`
--

LOCK TABLES `tag` WRITE;
/*!40000 ALTER TABLE `tag` DISABLE KEYS */;
INSERT INTO `tag` VALUES (1,'外向开朗','性格特点',1,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(2,'安静慢热','性格特点',2,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(3,'随和好相处','性格特点',3,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(4,'幽默有趣','性格特点',4,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(5,'理性冷静','性格特点',5,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(6,'感性细腻','性格特点',6,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(7,'真诚直接','性格特点',7,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(8,'耐心倾听','性格特点',8,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(9,'热情主动','性格特点',9,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(10,'独立自主','性格特点',10,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(11,'乐观积极','性格特点',11,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(12,'温柔体贴','性格特点',12,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(13,'认真负责','性格特点',13,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(14,'行动力强','性格特点',14,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(15,'深度聊天','社交偏好',1,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(16,'轻松闲聊','社交偏好',2,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(17,'线上交流','社交偏好',3,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(18,'线下活动','社交偏好',4,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(19,'小圈子社交','社交偏好',5,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(20,'群体活动','社交偏好',6,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(21,'主动分享','社交偏好',7,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(22,'倾听陪伴','社交偏好',8,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(23,'慢慢熟悉','社交偏好',9,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(24,'高频互动','社交偏好',10,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(25,'偶尔联系','社交偏好',11,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(26,'共同成长','社交偏好',12,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(27,'探索新鲜事','社交偏好',13,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(28,'周末见面','社交偏好',14,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(29,'前端开发','专业技能',1,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(30,'后端开发','专业技能',2,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(31,'移动开发','专业技能',3,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(32,'人工智能','专业技能',4,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(33,'数据分析','专业技能',5,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(34,'产品设计','专业技能',6,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(35,'视觉设计','专业技能',7,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(36,'网络安全','专业技能',8,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(37,'运维开发','专业技能',9,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(38,'内容创作','专业技能',10,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(39,'游戏开发','专业技能',11,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(40,'嵌入式开发','专业技能',12,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(41,'测试开发','专业技能',13,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(42,'数据库','专业技能',14,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(43,'项目管理','专业技能',15,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(44,'视频剪辑','专业技能',16,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(45,'新媒体运营','专业技能',17,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(46,'市场营销','专业技能',18,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(47,'考研备考','学习成长',1,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(48,'考公考编','学习成长',2,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(49,'语言学习','学习成长',3,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(50,'编程学习','学习成长',4,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(51,'阅读提升','学习成长',5,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(52,'自律打卡','学习成长',6,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(53,'论文科研','学习成长',7,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(54,'知识分享','学习成长',8,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(55,'职业规划','学习成长',9,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(56,'面试求职','学习成长',10,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(57,'证书考试','学习成长',11,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(58,'写作练习','学习成长',12,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(59,'时间管理','学习成长',13,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(60,'公开演讲','学习成长',14,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(61,'个人理财','学习成长',15,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(62,'创业交流','学习成长',16,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(63,'跑步','运动健身',1,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(64,'健身','运动健身',2,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(65,'篮球','运动健身',3,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(66,'足球','运动健身',4,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(67,'羽毛球','运动健身',5,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(68,'乒乓球','运动健身',6,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(69,'游泳','运动健身',7,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(70,'网球','运动健身',8,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(71,'瑜伽','运动健身',9,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(72,'跳舞','运动健身',10,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(73,'排球','运动健身',11,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(74,'台球','运动健身',12,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(75,'滑板','运动健身',13,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(76,'攀岩','运动健身',14,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(77,'拳击','运动健身',15,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(78,'普拉提','运动健身',16,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(79,'飞盘','运动健身',17,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(80,'跳绳','运动健身',18,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(81,'MOBA游戏','游戏娱乐',1,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(82,'射击游戏','游戏娱乐',2,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(83,'开放世界','游戏娱乐',3,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(84,'休闲游戏','游戏娱乐',4,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(85,'主机游戏','游戏娱乐',5,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(86,'独立游戏','游戏娱乐',6,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(87,'桌游','游戏娱乐',7,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(88,'剧本杀','游戏娱乐',8,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(89,'策略游戏','游戏娱乐',9,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(90,'角色扮演','游戏娱乐',10,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(91,'模拟经营','游戏娱乐',11,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(92,'音游','游戏娱乐',12,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(93,'卡牌游戏','游戏娱乐',13,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(94,'竞速游戏','游戏娱乐',14,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(95,'解谜游戏','游戏娱乐',15,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(96,'电竞赛事','游戏娱乐',16,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(97,'电影','影音文艺',1,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(98,'剧集','影音文艺',2,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(99,'动漫','影音文艺',3,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(100,'音乐','影音文艺',4,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(101,'演唱会','影音文艺',5,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(102,'播客','影音文艺',6,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(103,'摄影','影音文艺',7,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(104,'绘画','影音文艺',8,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(105,'写作','影音文艺',9,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(106,'纪录片','影音文艺',10,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(107,'音乐剧','影音文艺',11,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(108,'话剧','影音文艺',12,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(109,'唱歌','影音文艺',13,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(110,'乐器','影音文艺',14,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(111,'书法','影音文艺',15,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(112,'舞蹈','影音文艺',16,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(113,'短视频','影音文艺',17,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(114,'旅行','户外旅行',1,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(115,'徒步','户外旅行',2,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(116,'骑行','户外旅行',3,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(117,'露营','户外旅行',4,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(118,'爬山','户外旅行',5,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(119,'城市漫游','户外旅行',6,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(120,'自驾游','户外旅行',7,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(121,'自然探索','户外旅行',8,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(122,'登山','户外旅行',9,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(123,'钓鱼','户外旅行',10,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(124,'观鸟','户外旅行',11,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(125,'滑雪','户外旅行',12,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(126,'冲浪','户外旅行',13,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(127,'潜水','户外旅行',14,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(128,'野餐','户外旅行',15,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(129,'看日出','户外旅行',16,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(130,'美食','生活兴趣',1,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(131,'咖啡','生活兴趣',2,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(132,'烘焙','生活兴趣',3,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(133,'做饭','生活兴趣',4,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(134,'宠物','生活兴趣',5,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(135,'养花','生活兴趣',6,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(136,'穿搭','生活兴趣',7,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(137,'数码','生活兴趣',8,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(138,'手工','生活兴趣',9,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(139,'家居','生活兴趣',10,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(140,'园艺','生活兴趣',11,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(141,'茶饮','生活兴趣',12,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(142,'探店','生活兴趣',13,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(143,'收藏','生活兴趣',14,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(144,'汽车','生活兴趣',15,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(145,'整理收纳','生活兴趣',16,'2026-06-12 00:58:07','2026-06-12 00:58:07',0),(146,'健康养生','生活兴趣',17,'2026-06-12 00:58:07','2026-06-12 00:58:07',0);
/*!40000 ALTER TABLE `tag` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `username` varchar(256) DEFAULT NULL COMMENT '用户呢称',
  `userAccount` varchar(256) DEFAULT NULL COMMENT '用户账号',
  `avatarUrl` varchar(1024) DEFAULT NULL COMMENT '用户头像',
  `gender` tinyint DEFAULT NULL COMMENT '用户性别',
  `userPassword` varchar(512) NOT NULL COMMENT '用户密码',
  `phone` varchar(128) DEFAULT NULL COMMENT '用户电话',
  `email` varchar(512) DEFAULT NULL COMMENT '用户邮箱',
  `userStatus` tinyint DEFAULT '0' COMMENT '用户状态（0 - 正常）',
  `createTime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '用户创建时间',
  `updateTime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '用户更新时间',
  `isDelete` tinyint DEFAULT '0' COMMENT '用户是否删除（0 - 未删除）',
  `userRole` int DEFAULT '0' COMMENT '用户角色（0 - 普通用户  1 - 管理员）',
  `totalScore` int NOT NULL DEFAULT '0' COMMENT '累计积分',
  `wins` int NOT NULL DEFAULT '0' COMMENT '累计胜局',
  `losses` int NOT NULL DEFAULT '0' COMMENT '累计负局',
  `winRate` decimal(5,4) NOT NULL DEFAULT '0.0000' COMMENT '胜率',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account` (`userAccount`)
) ENGINE=InnoDB AUTO_INCREMENT=3059 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (15,'玉桂狗','cinnamoroll','https://cinoo-matchmate.oss-cn-beijing.aliyuncs.com/userAvatar/15/cf0f4903-54bf-453d-9c8a-2d652ce4fed6.jpg',1,'$2a$10$6MusLzWp8YZZI18fifLacOEr673EDcW54fnumEzYX21V4t4a.xLQO','18800000001','cinnamoroll@example.com',0,'2026-06-01 09:18:00','2026-06-14 20:01:28',0,0,0,0,0,0.0000),(16,'库洛米','kuromi','https://cinoo-matchmate.oss-cn-beijing.aliyuncs.com/userAvatar/16/6ef3485b-af12-43fa-b7a8-d4b8805c0940.jpg',2,'$2a$10$6MusLzWp8YZZI18fifLacOEr673EDcW54fnumEzYX21V4t4a.xLQO','18800000002','kuromi@example.com',0,'2026-06-02 14:26:00','2026-06-14 20:01:28',0,0,0,0,0,0.0000),(17,'美乐蒂','mymelody','https://cinoo-matchmate.oss-cn-beijing.aliyuncs.com/userAvatar/17/afaa24e1-0a78-4234-893b-8afac6a9eaae.jpg',2,'$2a$10$6MusLzWp8YZZI18fifLacOEr673EDcW54fnumEzYX21V4t4a.xLQO','18800000003','mymelody@example.com',0,'2026-06-03 10:42:00','2026-06-12 18:51:52',0,0,0,0,0,0.0000),(18,'凯蒂猫','hellokitty','https://cinoo-matchmate.oss-cn-beijing.aliyuncs.com/userAvatar/18/23d07ed0-12da-4577-8a9c-849700e7d9b6.jpg',2,'$2a$10$6MusLzWp8YZZI18fifLacOEr673EDcW54fnumEzYX21V4t4a.xLQO','18800000004','hellokitty@example.com',0,'2026-06-04 16:05:00','2026-06-12 18:51:52',0,0,0,0,0,0.0000),(19,'布丁狗','pompompurin','https://cinoo-matchmate.oss-cn-beijing.aliyuncs.com/userAvatar/19/75df0e92-52fa-4891-aa77-f047544775a5.jpg',1,'$2a$10$6MusLzWp8YZZI18fifLacOEr673EDcW54fnumEzYX21V4t4a.xLQO','18800000005','pompompurin@example.com',0,'2026-06-05 11:37:00','2026-06-12 18:51:52',0,0,0,0,0,0.0000),(20,'帕恰狗','pochacco','https://cinoo-matchmate.oss-cn-beijing.aliyuncs.com/userAvatar/20/15d88483-f17f-421a-a770-79c08e29a4c6.jpg',1,'$2a$10$6MusLzWp8YZZI18fifLacOEr673EDcW54fnumEzYX21V4t4a.xLQO','18800000006','pochacco@example.com',0,'2026-06-06 08:50:00','2026-06-12 18:51:52',0,0,0,0,0,0.0000),(21,'许愿兔','wishmemell','https://cinoo-matchmate.oss-cn-beijing.aliyuncs.com/userAvatar/21/7ec2ed96-2b88-4813-971b-1a229dee3f71.jpg',2,'$2a$10$6MusLzWp8YZZI18fifLacOEr673EDcW54fnumEzYX21V4t4a.xLQO','18800000007','wishmemell@example.com',0,'2026-06-07 19:12:00','2026-06-12 18:51:52',0,0,0,0,0,0.0000),(22,'人鱼汉顿','hangyodon','https://cinoo-matchmate.oss-cn-beijing.aliyuncs.com/userAvatar/22/31cb62fe-575b-4459-b24b-dcb6424722bf.jpg',1,'$2a$10$6MusLzWp8YZZI18fifLacOEr673EDcW54fnumEzYX21V4t4a.xLQO','18800000008','hangyodon@example.com',0,'2026-06-08 13:33:00','2026-06-12 18:51:52',0,0,0,0,0,0.0000),(23,'酷企鹅','badtzmaru','https://cinoo-matchmate.oss-cn-beijing.aliyuncs.com/userAvatar/23/748760a3-743b-4407-9b29-48509f16375f.jpg',1,'$2a$10$6MusLzWp8YZZI18fifLacOEr673EDcW54fnumEzYX21V4t4a.xLQO','18800000009','badtzmaru@example.com',0,'2026-06-09 21:08:00','2026-06-12 18:51:52',0,0,0,0,0,0.0000),(24,'大眼蛙','keroppi','https://cinoo-matchmate.oss-cn-beijing.aliyuncs.com/userAvatar/24/f22d4852-c8fa-4542-9876-cda4e6ae015d.jpg',1,'$2a$10$6MusLzWp8YZZI18fifLacOEr673EDcW54fnumEzYX21V4t4a.xLQO','18800000010','keroppi@example.com',0,'2026-06-10 15:45:00','2026-06-12 18:51:52',0,0,0,0,0,0.0000),(25,'Admin','admin','https://cinoo-matchmate.oss-cn-beijing.aliyuncs.com/userAvatar/25/e6463419-4faa-4c4d-8496-507a5527fc5a.jpg',1,'$2a$10$6MusLzWp8YZZI18fifLacOEr673EDcW54fnumEzYX21V4t4a.xLQO','18800000011','admin@example.com',0,'2026-06-12 05:06:41','2026-06-14 20:01:28',0,1,0,0,0,0.0000),(26,NULL,'testDel',NULL,NULL,'$2a$10$6MusLzWp8YZZI18fifLacOEr673EDcW54fnumEzYX21V4t4a.xLQO',NULL,NULL,0,'2026-06-12 05:30:54','2026-06-12 14:28:21',1,0,0,0,0,0.0000),(1350,NULL,'codextest','https://cinoo-matchmate.oss-cn-beijing.aliyuncs.com/userAvatar/1350/2859139c-a8bd-4986-a7c5-1142888c92f9.jpg',NULL,'$2a$10$6MusLzWp8YZZI18fifLacOEr673EDcW54fnumEzYX21V4t4a.xLQO',NULL,NULL,1,'2026-06-13 16:09:32','2026-06-14 20:01:28',0,0,0,0,0,0.0000),(2887,'testovo','testovo',NULL,1,'$2a$10$6MusLzWp8YZZI18fifLacOEr673EDcW54fnumEzYX21V4t4a.xLQO',NULL,NULL,2,'2026-06-14 16:07:26','2026-06-14 16:07:54',1,0,0,0,0,0.0000);
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_tag`
--

DROP TABLE IF EXISTS `user_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `userId` bigint NOT NULL COMMENT '用户 id',
  `tagId` bigint NOT NULL COMMENT '标签 id',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tag` (`userId`,`tagId`),
  KEY `idx_user_tag_tag_id` (`tagId`),
  CONSTRAINT `fk_user_tag_tag` FOREIGN KEY (`tagId`) REFERENCES `tag` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_tag_user` FOREIGN KEY (`userId`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户标签关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_tag`
--

LOCK TABLES `user_tag` WRITE;
/*!40000 ALTER TABLE `user_tag` DISABLE KEYS */;
INSERT INTO `user_tag` VALUES (4,16,1,'2026-06-12 00:58:07'),(5,16,100,'2026-06-12 00:58:07'),(6,16,136,'2026-06-12 00:58:07'),(7,17,12,'2026-06-12 00:58:07'),(8,17,132,'2026-06-12 00:58:07'),(9,17,138,'2026-06-12 00:58:07'),(10,18,9,'2026-06-12 00:58:07'),(11,18,133,'2026-06-12 00:58:07'),(12,18,110,'2026-06-12 00:58:07'),(13,19,3,'2026-06-12 00:58:07'),(14,19,130,'2026-06-12 00:58:07'),(15,19,22,'2026-06-12 00:58:07'),(16,20,11,'2026-06-12 00:58:07'),(17,20,63,'2026-06-12 00:58:07'),(18,20,65,'2026-06-12 00:58:07'),(19,21,6,'2026-06-12 00:58:07'),(20,21,105,'2026-06-12 00:58:07'),(21,21,17,'2026-06-12 00:58:07'),(22,22,4,'2026-06-12 00:58:07'),(23,22,69,'2026-06-12 00:58:07'),(24,22,15,'2026-06-12 00:58:07'),(25,23,5,'2026-06-12 00:58:07'),(26,23,85,'2026-06-12 00:58:07'),(27,23,10,'2026-06-12 00:58:07'),(28,24,14,'2026-06-12 00:58:07'),(29,24,121,'2026-06-12 00:58:07'),(30,24,20,'2026-06-12 00:58:07'),(35,15,2,'2026-06-12 01:08:36'),(36,15,131,'2026-06-12 01:08:36'),(37,15,16,'2026-06-12 01:08:36'),(38,25,142,'2026-06-12 05:13:38');
/*!40000 ALTER TABLE `user_tag` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'matchmate'
--

--
-- Dumping routines for database 'matchmate'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-14 20:37:19
