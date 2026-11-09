CREATE DATABASE  IF NOT EXISTS `peergeek` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `peergeek`;
-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: peergeek
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `assignment_questions`
--

DROP TABLE IF EXISTS `assignment_questions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assignment_questions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assignment_id` bigint NOT NULL,
  `position` int NOT NULL,
  `type` enum('MCQ_SINGLE','MCQ_MULTI','DESCRIPTIVE','CODE') NOT NULL,
  `prompt` text NOT NULL,
  `points` decimal(7,2) NOT NULL,
  `options` json DEFAULT NULL,
  `code_language` varchar(30) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_assignment_questions_position` (`assignment_id`,`position`),
  CONSTRAINT `fk_assignment_questions_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignments` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `assignment_questions`
--

LOCK TABLES `assignment_questions` WRITE;
/*!40000 ALTER TABLE `assignment_questions` DISABLE KEYS */;
/*!40000 ALTER TABLE `assignment_questions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `assignments`
--

DROP TABLE IF EXISTS `assignments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `module_id` bigint DEFAULT NULL,
  `lesson_id` bigint DEFAULT NULL,
  `kind` enum('PROJECT','QUESTIONS') NOT NULL,
  `title` varchar(255) NOT NULL,
  `instructions` text NOT NULL,
  `status` enum('DRAFT','PUBLISHED','UNPUBLISHED') NOT NULL,
  `max_points` decimal(7,2) NOT NULL,
  `opens_at` timestamp NULL DEFAULT NULL,
  `due_at` timestamp NULL DEFAULT NULL,
  `time_limit_minutes` int DEFAULT NULL,
  `allow_late` tinyint(1) NOT NULL DEFAULT '0',
  `allow_text` tinyint(1) NOT NULL DEFAULT '0',
  `allow_files` tinyint(1) NOT NULL DEFAULT '0',
  `show_correct_answers` tinyint(1) NOT NULL DEFAULT '0',
  `attempt_count` int NOT NULL DEFAULT '0',
  `published_at` timestamp NULL DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_by` bigint NOT NULL,
  `created_at` timestamp NOT NULL,
  `updated_at` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_assignments_user` (`created_by`),
  KEY `idx_assignments_course_status` (`course_id`,`status`),
  KEY `idx_assignments_due_at` (`due_at`),
  KEY `idx_assignments_lesson_id` (`lesson_id`),
  KEY `idx_assignments_module_id` (`module_id`),
  CONSTRAINT `fk_assignments_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_assignments_user` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_assignments_module_lesson` CHECK (((`module_id` is null) or (`lesson_id` is null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `assignments`
--

LOCK TABLES `assignments` WRITE;
/*!40000 ALTER TABLE `assignments` DISABLE KEYS */;
/*!40000 ALTER TABLE `assignments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_logs`
--

DROP TABLE IF EXISTS `audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_user_id` bigint DEFAULT NULL,
  `action` varchar(80) NOT NULL,
  `entity_type` varchar(80) NOT NULL,
  `entity_id` varchar(128) DEFAULT NULL,
  `details` json NOT NULL,
  `ip` varchar(45) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_logs_actor_created` (`actor_user_id`,`created_at`),
  KEY `idx_audit_logs_entity` (`entity_type`,`entity_id`),
  KEY `idx_audit_logs_action_created` (`action`,`created_at`),
  CONSTRAINT `fk_audit_logs_actor` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_logs`
--

LOCK TABLES `audit_logs` WRITE;
/*!40000 ALTER TABLE `audit_logs` DISABLE KEYS */;
INSERT INTO `audit_logs` VALUES (1,NULL,'AUTH_LOGIN_FAILED','USER',NULL,'{}','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','2026-10-20 17:42:27.794409'),(2,1,'USER_REGISTERED','USER','1','{}','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','2026-10-20 17:49:26.902512'),(3,1,'AUTH_LOGOUT','USER','1','{}','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','2026-10-20 22:05:11.659666'),(4,1,'AUTH_LOGIN_SUCCESS','USER','1','{}','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','2026-10-20 22:05:32.563706'),(5,1,'AUTH_LOGOUT','USER','1','{}','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','2026-10-20 22:51:45.232526'),(6,1,'AUTH_LOGIN_SUCCESS','USER','1','{}','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','2026-10-21 05:10:09.904352');
/*!40000 ALTER TABLE `audit_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `categories`
--

DROP TABLE IF EXISTS `categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(80) NOT NULL,
  `slug` varchar(100) NOT NULL,
  `sort_order` int NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_categories_name` (`name`),
  UNIQUE KEY `uk_categories_slug` (`slug`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `categories`
--

LOCK TABLES `categories` WRITE;
/*!40000 ALTER TABLE `categories` DISABLE KEYS */;
INSERT INTO `categories` VALUES (1,'Programming','programming',0,1,'2026-10-20 16:59:20.395545','2026-10-20 16:59:20.395545'),(2,'Web Development','web-development',1,1,'2026-10-20 16:59:20.395545','2026-10-20 16:59:20.395545'),(3,'Data Science','data-science',2,1,'2026-10-20 16:59:20.395545','2026-10-20 16:59:20.395545'),(4,'Design','design',3,1,'2026-10-20 16:59:20.395545','2026-10-20 16:59:20.395545'),(5,'Business','business',4,1,'2026-10-20 16:59:20.395545','2026-10-20 16:59:20.395545'),(6,'Mathematics','mathematics',5,1,'2026-10-20 16:59:20.395545','2026-10-20 16:59:20.395545'),(7,'Science','science',6,1,'2026-10-20 16:59:20.395545','2026-10-20 16:59:20.395545'),(8,'Languages','languages',7,1,'2026-10-20 16:59:20.395545','2026-10-20 16:59:20.395545'),(9,'Personal Development','personal-development',8,1,'2026-10-20 16:59:20.395545','2026-10-20 16:59:20.395545'),(10,'Other','other',9,1,'2026-10-20 16:59:20.395545','2026-10-20 16:59:20.395545');
/*!40000 ALTER TABLE `categories` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_modules`
--

DROP TABLE IF EXISTS `course_modules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_modules` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `title` varchar(120) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `position` int NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_course_modules_course_position` (`course_id`,`position`),
  CONSTRAINT `fk_course_modules_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_modules`
--

LOCK TABLES `course_modules` WRITE;
/*!40000 ALTER TABLE `course_modules` DISABLE KEYS */;
/*!40000 ALTER TABLE `course_modules` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_progress`
--

DROP TABLE IF EXISTS `course_progress`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_progress` (
  `enrollment_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `total_lessons` int NOT NULL DEFAULT '0',
  `completed_lessons` int NOT NULL DEFAULT '0',
  `percent` tinyint NOT NULL DEFAULT '0',
  `completed_at` timestamp(6) NULL DEFAULT NULL,
  `stale` tinyint(1) NOT NULL DEFAULT '0',
  `computed_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`enrollment_id`),
  KEY `idx_course_progress_course_stale` (`course_id`,`stale`),
  CONSTRAINT `fk_course_progress_enrollment` FOREIGN KEY (`enrollment_id`) REFERENCES `enrollments` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_progress`
--

LOCK TABLES `course_progress` WRITE;
/*!40000 ALTER TABLE `course_progress` DISABLE KEYS */;
/*!40000 ALTER TABLE `course_progress` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_public_stats`
--

DROP TABLE IF EXISTS `course_public_stats`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_public_stats` (
  `course_id` bigint NOT NULL,
  `enrollment_count` int DEFAULT NULL,
  `completion_count` int DEFAULT NULL,
  `rating_average` decimal(3,2) DEFAULT NULL,
  `rating_count` int DEFAULT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_public_stats`
--

LOCK TABLES `course_public_stats` WRITE;
/*!40000 ALTER TABLE `course_public_stats` DISABLE KEYS */;
/*!40000 ALTER TABLE `course_public_stats` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_publish_locks`
--

DROP TABLE IF EXISTS `course_publish_locks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_publish_locks` (
  `course_id` bigint NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`course_id`),
  CONSTRAINT `fk_course_publish_locks_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_publish_locks`
--

LOCK TABLES `course_publish_locks` WRITE;
/*!40000 ALTER TABLE `course_publish_locks` DISABLE KEYS */;
/*!40000 ALTER TABLE `course_publish_locks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_topics`
--

DROP TABLE IF EXISTS `course_topics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_topics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `position` int NOT NULL,
  `name` varchar(40) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_course_topics_course` (`course_id`),
  CONSTRAINT `fk_course_topics_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_topics`
--

LOCK TABLES `course_topics` WRITE;
/*!40000 ALTER TABLE `course_topics` DISABLE KEYS */;
/*!40000 ALTER TABLE `course_topics` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `courses`
--

DROP TABLE IF EXISTS `courses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `courses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_id` bigint NOT NULL,
  `title` varchar(120) NOT NULL,
  `description` text,
  `category_id` bigint DEFAULT NULL,
  `difficulty` enum('BEGINNER','INTERMEDIATE','ADVANCED') DEFAULT NULL,
  `status` enum('DRAFT','PUBLISHED','UNPUBLISHED') NOT NULL DEFAULT 'DRAFT',
  `thumbnail_resource_id` bigint DEFAULT NULL,
  `published_at` timestamp(6) NULL DEFAULT NULL,
  `content_updated_at` timestamp(6) NOT NULL,
  `deleted_at` timestamp(6) NULL DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_courses_owner_deleted_updated` (`owner_id`,`deleted_at`,`updated_at`),
  KEY `idx_courses_status` (`status`),
  KEY `idx_courses_category` (`category_id`),
  CONSTRAINT `fk_courses_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`),
  CONSTRAINT `fk_courses_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `courses`
--

LOCK TABLES `courses` WRITE;
/*!40000 ALTER TABLE `courses` DISABLE KEYS */;
/*!40000 ALTER TABLE `courses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `enrollments`
--

DROP TABLE IF EXISTS `enrollments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `enrollments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `status` enum('ACTIVE','WITHDRAWN') NOT NULL DEFAULT 'ACTIVE',
  `enrolled_at` timestamp(6) NOT NULL,
  `withdrawn_at` timestamp(6) NULL DEFAULT NULL,
  `last_lesson_id` bigint DEFAULT NULL,
  `last_accessed_at` timestamp(6) NULL DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_enrollments_user_course` (`user_id`,`course_id`),
  KEY `idx_enrollments_user_status_updated` (`user_id`,`status`,`updated_at`),
  KEY `idx_enrollments_course_status` (`course_id`,`status`),
  CONSTRAINT `fk_enrollments_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `fk_enrollments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `enrollments`
--

LOCK TABLES `enrollments` WRITE;
/*!40000 ALTER TABLE `enrollments` DISABLE KEYS */;
/*!40000 ALTER TABLE `enrollments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'0.001','baseline','SQL','V0_001__baseline.sql',829321473,'root','2026-10-20 16:59:19',4,1),(2,'1.001','create users','SQL','V1_001__create_users.sql',-318852167,'root','2026-10-20 16:59:20',52,1),(3,'1.002','create refresh tokens','SQL','V1_002__create_refresh_tokens.sql',1905689059,'root','2026-10-20 16:59:20',52,1),(4,'1.003','create password reset otps','SQL','V1_003__create_password_reset_otps.sql',-1671324403,'root','2026-10-20 16:59:20',45,1),(5,'1.004','create audit logs','SQL','V1_004__create_audit_logs.sql',910306145,'root','2026-10-20 16:59:20',80,1),(6,'2.001','create categories','SQL','V2_001__create_categories.sql',1599094337,'root','2026-10-20 16:59:20',48,1),(7,'2.002','seed categories','SQL','V2_002__seed_categories.sql',-1260102942,'root','2026-10-20 16:59:20',4,1),(8,'2.003','create courses','SQL','V2_003__create_courses.sql',-2027886680,'root','2026-10-20 16:59:20',48,1),(9,'2.004','create course topics','SQL','V2_004__create_course_topics.sql',-744913768,'root','2026-10-20 16:59:20',40,1),(10,'2.005','create course modules','SQL','V2_005__create_course_modules.sql',1303478544,'root','2026-10-20 16:59:20',34,1),(11,'2.006','create lessons','SQL','V2_006__create_lessons.sql',-806331150,'root','2026-10-20 16:59:20',36,1),(12,'2.007','create lesson contents','SQL','V2_007__create_lesson_contents.sql',-1287326425,'root','2026-10-20 16:59:20',25,1),(13,'3.001','create resources','SQL','V3_001__create_resources.sql',1799905950,'root','2026-10-20 16:59:20',54,1),(14,'3.002','create lesson expected resources','SQL','V3_002__create_lesson_expected_resources.sql',20104297,'root','2026-10-20 16:59:20',31,1),(15,'3.003','track resource processing attempts','SQL','V3_003__track_resource_processing_attempts.sql',1248001142,'root','2026-10-20 16:59:20',71,1),(16,'4.001','create readiness runs','SQL','V4_001__create_readiness_runs.sql',-1450802114,'root','2026-10-20 16:59:20',37,1),(17,'4.002','create readiness issues','SQL','V4_002__create_readiness_issues.sql',-1498580750,'root','2026-10-20 16:59:20',35,1),(18,'4.003','create resource probe results','SQL','V4_003__create_resource_probe_results.sql',997118038,'root','2026-10-20 16:59:21',39,1),(19,'4.004','create link check results','SQL','V4_004__create_link_check_results.sql',-1994879407,'root','2026-10-20 16:59:21',37,1),(20,'4.005','create course publish locks','SQL','V4_005__create_course_publish_locks.sql',-1627902633,'root','2026-10-20 16:59:21',20,1),(21,'5.001','create public course index','SQL','V5_001__create_public_course_index.sql',-778790956,'root','2026-10-20 16:59:21',44,1),(22,'5.002','create course public stats','SQL','V5_002__create_course_public_stats.sql',1897219167,'root','2026-10-20 16:59:21',21,1),(23,'6.001','create enrollments','SQL','V6_001__create_enrollments.sql',1496651431,'root','2026-10-20 16:59:21',63,1),(24,'6.002','create lesson progress','SQL','V6_002__create_lesson_progress.sql',-1796715130,'root','2026-10-20 16:59:21',42,1),(25,'6.003','create course progress','SQL','V6_003__create_course_progress.sql',1555546047,'root','2026-10-20 16:59:21',40,1),(26,'7.001','create assignments','SQL','V7_001__create_assignments.sql',-1241437973,'root','2026-10-20 16:59:21',114,1),(27,'7.002','create assignment questions','SQL','V7_002__create_assignment_questions.sql',-703845936,'root','2026-10-20 16:59:21',56,1),(28,'7.003','create submissions','SQL','V7_003__create_submissions.sql',-1703198453,'root','2026-10-20 16:59:21',98,1),(29,'7.004','create submission answers','SQL','V7_004__create_submission_answers.sql',-1354991842,'root','2026-10-20 16:59:21',35,1),(30,'7.005','create submission files','SQL','V7_005__create_submission_files.sql',415279028,'root','2026-10-20 16:59:21',76,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lesson_contents`
--

DROP TABLE IF EXISTS `lesson_contents`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lesson_contents` (
  `lesson_id` bigint NOT NULL,
  `content_format` varchar(32) NOT NULL,
  `content_document` json NOT NULL,
  `word_count` int NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`lesson_id`),
  CONSTRAINT `fk_lesson_contents_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lesson_contents`
--

LOCK TABLES `lesson_contents` WRITE;
/*!40000 ALTER TABLE `lesson_contents` DISABLE KEYS */;
/*!40000 ALTER TABLE `lesson_contents` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lesson_expected_resources`
--

DROP TABLE IF EXISTS `lesson_expected_resources`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lesson_expected_resources` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lesson_id` bigint NOT NULL,
  `resource_type` varchar(16) NOT NULL,
  `min_count` int NOT NULL,
  `label` varchar(80) DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_expected_resources_lesson_type` (`lesson_id`,`resource_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lesson_expected_resources`
--

LOCK TABLES `lesson_expected_resources` WRITE;
/*!40000 ALTER TABLE `lesson_expected_resources` DISABLE KEYS */;
/*!40000 ALTER TABLE `lesson_expected_resources` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lesson_progress`
--

DROP TABLE IF EXISTS `lesson_progress`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lesson_progress` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enrollment_id` bigint NOT NULL,
  `lesson_id` bigint NOT NULL,
  `completed_at` timestamp(6) NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_lesson_progress_enrollment_lesson` (`enrollment_id`,`lesson_id`),
  KEY `idx_lesson_progress_lesson` (`lesson_id`),
  CONSTRAINT `fk_lesson_progress_enrollment` FOREIGN KEY (`enrollment_id`) REFERENCES `enrollments` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lesson_progress`
--

LOCK TABLES `lesson_progress` WRITE;
/*!40000 ALTER TABLE `lesson_progress` DISABLE KEYS */;
/*!40000 ALTER TABLE `lesson_progress` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lessons`
--

DROP TABLE IF EXISTS `lessons`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lessons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `module_id` bigint NOT NULL,
  `title` varchar(150) NOT NULL,
  `summary` varchar(500) DEFAULT NULL,
  `position` int NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_lessons_module_position` (`module_id`,`position`),
  CONSTRAINT `fk_lessons_module` FOREIGN KEY (`module_id`) REFERENCES `course_modules` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lessons`
--

LOCK TABLES `lessons` WRITE;
/*!40000 ALTER TABLE `lessons` DISABLE KEYS */;
/*!40000 ALTER TABLE `lessons` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `link_check_results`
--

DROP TABLE IF EXISTS `link_check_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `link_check_results` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource_id` bigint NOT NULL,
  `url_sha256` char(64) NOT NULL,
  `outcome` varchar(32) NOT NULL,
  `http_status` int DEFAULT NULL,
  `checked_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_link_check_resource` (`resource_id`),
  KEY `idx_link_check_url_checked` (`url_sha256`,`checked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `link_check_results`
--

LOCK TABLES `link_check_results` WRITE;
/*!40000 ALTER TABLE `link_check_results` DISABLE KEYS */;
/*!40000 ALTER TABLE `link_check_results` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `password_reset_otps`
--

DROP TABLE IF EXISTS `password_reset_otps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `password_reset_otps` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `otp_hash` varchar(100) NOT NULL,
  `expires_at` timestamp(6) NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `consumed_at` timestamp(6) NULL DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_password_reset_otps_user_created` (`user_id`,`created_at`),
  CONSTRAINT `fk_password_reset_otps_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `password_reset_otps`
--

LOCK TABLES `password_reset_otps` WRITE;
/*!40000 ALTER TABLE `password_reset_otps` DISABLE KEYS */;
/*!40000 ALTER TABLE `password_reset_otps` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `public_course_index`
--

DROP TABLE IF EXISTS `public_course_index`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `public_course_index` (
  `course_id` bigint NOT NULL,
  `title` varchar(120) NOT NULL,
  `short_description` varchar(300) DEFAULT NULL,
  `search_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `category_id` bigint DEFAULT NULL,
  `category_name` varchar(80) DEFAULT NULL,
  `category_slug` varchar(100) DEFAULT NULL,
  `difficulty` varchar(16) DEFAULT NULL,
  `topics_text` varchar(1000) DEFAULT NULL,
  `instructor_id` bigint NOT NULL,
  `instructor_name` varchar(120) DEFAULT NULL,
  `module_count` int NOT NULL DEFAULT '0',
  `lesson_count` int NOT NULL DEFAULT '0',
  `thumbnail_resource_id` bigint DEFAULT NULL,
  `published_at` timestamp(6) NOT NULL,
  `source_fingerprint` char(64) NOT NULL,
  `indexed_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`course_id`),
  KEY `idx_public_course_cat_published` (`category_id`,`published_at`),
  KEY `idx_public_course_difficulty` (`difficulty`),
  KEY `idx_public_course_published_at` (`published_at`),
  KEY `idx_public_course_title` (`title`(50))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `public_course_index`
--

LOCK TABLES `public_course_index` WRITE;
/*!40000 ALTER TABLE `public_course_index` DISABLE KEYS */;
/*!40000 ALTER TABLE `public_course_index` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `readiness_issues`
--

DROP TABLE IF EXISTS `readiness_issues`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `readiness_issues` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` bigint NOT NULL,
  `rule_code` varchar(80) NOT NULL,
  `severity` enum('BLOCKING_ERROR','WARNING') NOT NULL,
  `scope` enum('COURSE','MODULE','LESSON','RESOURCE') NOT NULL,
  `module_id` bigint DEFAULT NULL,
  `lesson_id` bigint DEFAULT NULL,
  `resource_id` bigint DEFAULT NULL,
  `module_title` varchar(150) DEFAULT NULL,
  `lesson_title` varchar(150) DEFAULT NULL,
  `resource_title` varchar(255) DEFAULT NULL,
  `message` varchar(1000) NOT NULL,
  `fix_hint` varchar(1000) DEFAULT NULL,
  `details` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_readiness_issues_run_severity` (`run_id`,`severity`,`id`),
  CONSTRAINT `fk_readiness_issues_run` FOREIGN KEY (`run_id`) REFERENCES `readiness_runs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `readiness_issues`
--

LOCK TABLES `readiness_issues` WRITE;
/*!40000 ALTER TABLE `readiness_issues` DISABLE KEYS */;
/*!40000 ALTER TABLE `readiness_issues` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `readiness_runs`
--

DROP TABLE IF EXISTS `readiness_runs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `readiness_runs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `requested_by` bigint NOT NULL,
  `status` enum('RUNNING','COMPLETED','FAILED') NOT NULL,
  `fingerprint` char(64) NOT NULL,
  `blocking_count` int NOT NULL DEFAULT '0',
  `warning_count` int NOT NULL DEFAULT '0',
  `failure_reason` varchar(1000) DEFAULT NULL,
  `started_at` timestamp(6) NOT NULL,
  `finished_at` timestamp(6) NULL DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_readiness_runs_requested_by` (`requested_by`),
  KEY `idx_readiness_runs_course_created` (`course_id`,`created_at`),
  CONSTRAINT `fk_readiness_runs_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_readiness_runs_requested_by` FOREIGN KEY (`requested_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `readiness_runs`
--

LOCK TABLES `readiness_runs` WRITE;
/*!40000 ALTER TABLE `readiness_runs` DISABLE KEYS */;
/*!40000 ALTER TABLE `readiness_runs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `refresh_tokens`
--

DROP TABLE IF EXISTS `refresh_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refresh_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `token_hash` char(64) NOT NULL,
  `family_id` char(36) NOT NULL,
  `expires_at` timestamp(6) NOT NULL,
  `revoked_at` timestamp(6) NULL DEFAULT NULL,
  `replaced_by` bigint DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `ip` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_tokens_hash` (`token_hash`),
  KEY `fk_refresh_tokens_replaced_by` (`replaced_by`),
  KEY `idx_refresh_tokens_user_family` (`user_id`,`family_id`),
  KEY `idx_refresh_tokens_expiry` (`expires_at`),
  CONSTRAINT `fk_refresh_tokens_replaced_by` FOREIGN KEY (`replaced_by`) REFERENCES `refresh_tokens` (`id`),
  CONSTRAINT `fk_refresh_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `refresh_tokens`
--

LOCK TABLES `refresh_tokens` WRITE;
/*!40000 ALTER TABLE `refresh_tokens` DISABLE KEYS */;
INSERT INTO `refresh_tokens` VALUES (1,1,'ece688bc0738cfb0311472f535b0cf3db3ba081f15296ff56c0c4147bf1eab13','ee7e164a-ce89-4461-accd-91c182b9eccf','2026-11-03 17:49:26.919365','2026-10-20 17:56:17.310964',2,'2026-10-20 17:49:26.919365','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','0:0:0:0:0:0:0:1'),(2,1,'98e724e5c09c1d36cdc4cc14aabe6f101d2955256efec94af56db8b0c7e5ea80','ee7e164a-ce89-4461-accd-91c182b9eccf','2026-11-03 17:56:17.310964','2026-10-20 18:33:41.491099',3,'2026-10-20 17:56:17.316157','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','0:0:0:0:0:0:0:1'),(3,1,'cb44988666af98a4737e0b8efe2b7048f09e77d7766f33c50f2c14b9a935a2a5','ee7e164a-ce89-4461-accd-91c182b9eccf','2026-11-03 18:33:41.491099','2026-10-20 18:41:42.552608',4,'2026-10-20 18:33:41.492717','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','0:0:0:0:0:0:0:1'),(4,1,'fb74263711ce5c7ff87741b8064e8a6ea4eb968fe6a7a3c0948129e52f3f3c89','ee7e164a-ce89-4461-accd-91c182b9eccf','2026-11-03 18:41:42.552608','2026-10-20 18:42:05.740022',5,'2026-10-20 18:41:42.557368','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','0:0:0:0:0:0:0:1'),(5,1,'24e28a4d09a8270018ba30be0656d1137a18312c7de46d652ce0d9a684f0073e','ee7e164a-ce89-4461-accd-91c182b9eccf','2026-11-03 18:42:05.740022','2026-10-20 21:42:55.581629',6,'2026-10-20 18:42:05.742826','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','0:0:0:0:0:0:0:1'),(6,1,'25225f6c27b970f7158df3c8f97bd419b955573ea9e7c9d600bea20bf0a9ec6b','ee7e164a-ce89-4461-accd-91c182b9eccf','2026-11-03 21:42:55.581629','2026-10-20 22:05:07.747147',7,'2026-10-20 21:42:55.582419','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','0:0:0:0:0:0:0:1'),(7,1,'687b552606726cf421faa1d474711ae0cd5da0892a49e35e00b559e84704d8c9','ee7e164a-ce89-4461-accd-91c182b9eccf','2026-11-03 22:05:07.747147','2026-10-20 22:05:11.657907',NULL,'2026-10-20 22:05:07.750275','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','0:0:0:0:0:0:0:1'),(8,1,'ce2fb19a4740ebc41913800d3655e9aca99d6b471943f0c24515822e7216c10c','179eebb9-2939-4259-990f-631847baa956','2026-11-03 22:05:32.579460','2026-10-20 22:42:16.196393',9,'2026-10-20 22:05:32.579460','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','0:0:0:0:0:0:0:1'),(9,1,'76613100c7fa9f311a22a3a3c5d647af6f86d378acb4fb5a8a7f0372ca8bf4ce','179eebb9-2939-4259-990f-631847baa956','2026-11-03 22:42:16.196393','2026-10-20 22:51:40.594244',10,'2026-10-20 22:42:16.202384','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','0:0:0:0:0:0:0:1'),(10,1,'af39cb3fb61cf4e1f2dff3aac1dafec997bc315a511f5793a644a399efa099e9','179eebb9-2939-4259-990f-631847baa956','2026-11-03 22:51:40.594244','2026-10-20 22:51:45.232526',NULL,'2026-10-20 22:51:40.594244','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','0:0:0:0:0:0:0:1'),(11,1,'857a4e3c076ebd900e04c9827d63d50098fb0d5fec00cde545c6d2f23f7f1601','fc825c06-019a-48eb-ae5d-1fc8c9a5f71f','2026-11-04 05:10:09.947338',NULL,NULL,'2026-10-21 05:10:09.947338','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','0:0:0:0:0:0:0:1');
/*!40000 ALTER TABLE `refresh_tokens` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `resource_probe_results`
--

DROP TABLE IF EXISTS `resource_probe_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `resource_probe_results` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource_id` bigint NOT NULL,
  `checksum_sha256` char(64) NOT NULL,
  `kind` enum('VIDEO','PDF','OOXML','IMAGE') NOT NULL,
  `ok` tinyint(1) NOT NULL,
  `error_code` varchar(80) DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `width` int DEFAULT NULL,
  `height` int DEFAULT NULL,
  `info` json DEFAULT NULL,
  `probed_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_probe_checksum` (`resource_id`,`checksum_sha256`),
  KEY `idx_resource_probe_resource` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `resource_probe_results`
--

LOCK TABLES `resource_probe_results` WRITE;
/*!40000 ALTER TABLE `resource_probe_results` DISABLE KEYS */;
/*!40000 ALTER TABLE `resource_probe_results` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `resources`
--

DROP TABLE IF EXISTS `resources`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `resources` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `lesson_id` bigint DEFAULT NULL,
  `uploaded_by` bigint NOT NULL,
  `purpose` varchar(32) NOT NULL,
  `type` varchar(16) NOT NULL,
  `title` varchar(255) NOT NULL,
  `original_filename` varchar(255) DEFAULT NULL,
  `object_key` varchar(512) DEFAULT NULL,
  `mime_type` varchar(255) DEFAULT NULL,
  `size_bytes` bigint DEFAULT NULL,
  `checksum_sha256` char(64) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `processing_attempts` int NOT NULL DEFAULT '0',
  `failure_reason` varchar(64) DEFAULT NULL,
  `external_url` varchar(2048) DEFAULT NULL,
  `position` int NOT NULL DEFAULT '0',
  `deleted_at` timestamp(6) NULL DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resources_object_key` (`object_key`),
  KEY `idx_resources_lesson_deleted_position` (`lesson_id`,`deleted_at`,`position`),
  KEY `idx_resources_course_purpose_deleted` (`course_id`,`purpose`,`deleted_at`),
  KEY `idx_resources_status_updated` (`status`,`updated_at`),
  KEY `idx_resources_deleted_at` (`deleted_at`),
  KEY `fk_resources_uploaded_by` (`uploaded_by`),
  CONSTRAINT `fk_resources_uploaded_by` FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `resources`
--

LOCK TABLES `resources` WRITE;
/*!40000 ALTER TABLE `resources` DISABLE KEYS */;
/*!40000 ALTER TABLE `resources` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `submission_answers`
--

DROP TABLE IF EXISTS `submission_answers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `submission_answers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `submission_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  `answer_text` text,
  `selected_option_ids` json DEFAULT NULL,
  `auto_points` decimal(7,2) DEFAULT NULL,
  `points` decimal(7,2) DEFAULT NULL,
  `feedback` text,
  `updated_at` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_submission_answers_submission_question` (`submission_id`,`question_id`),
  CONSTRAINT `fk_submission_answers_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `submission_answers`
--

LOCK TABLES `submission_answers` WRITE;
/*!40000 ALTER TABLE `submission_answers` DISABLE KEYS */;
/*!40000 ALTER TABLE `submission_answers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `submission_files`
--

DROP TABLE IF EXISTS `submission_files`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `submission_files` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `submission_id` bigint NOT NULL,
  `uploaded_by` bigint NOT NULL,
  `original_filename` varchar(255) NOT NULL,
  `object_key` varchar(512) NOT NULL,
  `mime_type` varchar(127) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `checksum_sha256` char(64) DEFAULT NULL,
  `status` enum('PENDING','READY','FAILED') NOT NULL,
  `failure_reason` varchar(64) DEFAULT NULL,
  `created_at` timestamp NOT NULL,
  `updated_at` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `object_key` (`object_key`),
  KEY `fk_submission_files_user` (`uploaded_by`),
  KEY `idx_submission_files_submission` (`submission_id`),
  KEY `idx_submission_files_status_created` (`status`,`created_at`),
  CONSTRAINT `fk_submission_files_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_submission_files_user` FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `submission_files`
--

LOCK TABLES `submission_files` WRITE;
/*!40000 ALTER TABLE `submission_files` DISABLE KEYS */;
/*!40000 ALTER TABLE `submission_files` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `submissions`
--

DROP TABLE IF EXISTS `submissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `submissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assignment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `status` enum('IN_PROGRESS','SUBMITTED','GRADED') NOT NULL,
  `started_at` timestamp NOT NULL,
  `deadline_at` timestamp NULL DEFAULT NULL,
  `submitted_at` timestamp NULL DEFAULT NULL,
  `late` tinyint(1) NOT NULL DEFAULT '0',
  `auto_submitted` tinyint(1) NOT NULL DEFAULT '0',
  `auto_graded` tinyint(1) NOT NULL DEFAULT '0',
  `text_answer` text,
  `marks` decimal(7,2) DEFAULT NULL,
  `total_points` decimal(7,2) DEFAULT NULL,
  `overall_feedback` text,
  `graded_by` bigint DEFAULT NULL,
  `graded_at` timestamp NULL DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` timestamp NOT NULL,
  `updated_at` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_submissions_assignment_user` (`assignment_id`,`user_id`),
  KEY `fk_submissions_graded_by` (`graded_by`),
  KEY `idx_submissions_assignment_status_submitted` (`assignment_id`,`status`,`submitted_at`),
  KEY `idx_submissions_user_status` (`user_id`,`status`),
  KEY `idx_submissions_status_deadline` (`status`,`deadline_at`),
  CONSTRAINT `fk_submissions_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_submissions_graded_by` FOREIGN KEY (`graded_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_submissions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `submissions`
--

LOCK TABLES `submissions` WRITE;
/*!40000 ALTER TABLE `submissions` DISABLE KEYS */;
/*!40000 ALTER TABLE `submissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(254) NOT NULL,
  `password_hash` varchar(100) NOT NULL,
  `display_name` varchar(100) NOT NULL,
  `headline` varchar(160) DEFAULT NULL,
  `bio` varchar(2000) DEFAULT NULL,
  `role` varchar(16) NOT NULL DEFAULT 'USER',
  `last_mode` varchar(16) NOT NULL DEFAULT 'STUDENT',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`),
  CONSTRAINT `chk_users_last_mode` CHECK ((`last_mode` in (_utf8mb4'STUDENT',_utf8mb4'INSTRUCTOR'))),
  CONSTRAINT `chk_users_role` CHECK ((`role` in (_utf8mb4'USER',_utf8mb4'ADMIN')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'devanshtyagicu@gmail.com','$2a$12$atv9RkvlhXK1BLaxmKYHjuj7j1Iq7MJAPvl.zDW0HzEPy138i95YK','DEVANSH',NULL,NULL,'USER','INSTRUCTOR',1,'2026-10-20 17:49:26.854069','2026-10-21 05:10:22.252991');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-21 10:59:05
