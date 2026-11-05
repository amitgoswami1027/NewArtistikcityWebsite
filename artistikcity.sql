-- MySQL dump 10.13  Distrib 8.4.13, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: artistikcity
-- ------------------------------------------------------
-- Server version	8.4.13

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
-- Table structure for table `admin_audit`
--

DROP TABLE IF EXISTS `admin_audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint DEFAULT NULL,
  `entity` varchar(50) NOT NULL,
  `entity_id` bigint DEFAULT NULL,
  `action` varchar(50) NOT NULL,
  `details` varchar(1000) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `admin_audit_entity_index` (`entity`,`entity_id`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `admin_audit`
--

LOCK TABLES `admin_audit` WRITE;
/*!40000 ALTER TABLE `admin_audit` DISABLE KEYS */;
INSERT INTO `admin_audit` VALUES (1,1,'shop_order',0,'status PACKED','AC-S-20261025-QS2XY4','2026-10-25 15:03:10'),(2,1,'course',2,'deleted','Acrylic Portrait Painting','2026-10-25 15:21:03'),(3,1,'course',112,'hidden',NULL,'2026-10-25 15:21:43'),(4,1,'course',113,'published',NULL,'2026-10-25 15:21:46'),(5,1,'course',111,'hidden',NULL,'2026-10-25 15:21:48'),(6,1,'course',110,'hidden',NULL,'2026-10-25 15:21:50'),(7,1,'course',109,'hidden',NULL,'2026-10-25 15:22:49'),(8,1,'course',108,'hidden',NULL,'2026-10-25 15:22:55'),(9,1,'course',107,'hidden',NULL,'2026-10-25 15:22:59'),(10,1,'course',1,'hidden',NULL,'2026-10-25 15:23:00'),(11,1,'course',3,'hidden',NULL,'2026-10-25 15:23:01'),(12,1,'course',112,'published',NULL,'2026-10-25 15:23:03'),(13,1,'course',113,'hidden',NULL,'2026-10-25 15:23:03'),(14,1,'course',111,'published',NULL,'2026-10-25 15:23:04'),(15,1,'course',110,'published',NULL,'2026-10-25 15:23:05'),(16,1,'course',109,'published',NULL,'2026-10-25 15:23:06'),(17,1,'course',108,'published',NULL,'2026-10-25 15:23:07'),(18,1,'course',107,'published',NULL,'2026-10-25 15:23:07'),(19,1,'course',1,'published',NULL,'2026-10-25 15:23:08'),(20,1,'course',3,'published',NULL,'2026-10-25 15:23:08'),(21,1,'course',112,'hidden',NULL,'2026-10-25 15:23:09'),(22,1,'course',106,'hidden',NULL,'2026-10-25 15:23:14'),(23,1,'course',113,'published',NULL,'2026-10-25 15:23:19'),(24,1,'course',111,'hidden',NULL,'2026-10-25 15:23:21'),(25,1,'course',105,'hidden',NULL,'2026-10-25 15:23:22'),(26,1,'course',104,'hidden',NULL,'2026-10-25 15:23:25'),(27,1,'course',103,'hidden',NULL,'2026-10-25 15:23:27'),(28,1,'course',101,'hidden',NULL,'2026-10-25 15:23:31'),(29,1,'course',108,'hidden',NULL,'2026-10-25 15:27:34'),(30,1,'course',113,'hidden',NULL,'2026-10-25 15:27:36'),(31,1,'course',3,'hidden',NULL,'2026-10-25 15:27:38'),(32,1,'course',1,'hidden',NULL,'2026-10-25 15:27:39'),(33,1,'course',107,'hidden',NULL,'2026-10-25 15:27:41'),(34,1,'course',109,'hidden',NULL,'2026-10-25 15:27:42'),(35,1,'mediums',5,'updated','Charcoal','2026-10-25 15:28:05'),(36,1,'mediums',6,'updated','Digital Art','2026-10-25 15:28:10'),(37,1,'mediums',102,'updated','Mixed Media','2026-10-25 15:28:14'),(38,1,'mediums',4,'updated','Oil Painting','2026-10-25 15:28:21'),(39,1,'mediums',3,'updated','Pencil Sketch','2026-10-25 15:28:26');
/*!40000 ALTER TABLE `admin_audit` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `admins`
--

DROP TABLE IF EXISTS `admins`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admins` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `slug` varchar(255) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `admin_type` varchar(20) NOT NULL,
  `location` varchar(255) DEFAULT NULL,
  `profile_photo` varchar(500) DEFAULT NULL,
  `profile_details` longtext,
  `title` longtext,
  `designation` longtext,
  `profile_title` varchar(255) DEFAULT NULL,
  `profile_description` longtext,
  `remember_token` varchar(100) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `admins_email_unique` (`email`),
  CONSTRAINT `admins_chk_1` CHECK ((`admin_type` in (_utf8mb4'admin',_utf8mb4'teacher',_utf8mb4'moderator')))
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `admins`
--

LOCK TABLES `admins` WRITE;
/*!40000 ALTER TABLE `admins` DISABLE KEYS */;
INSERT INTO `admins` VALUES (1,'Site Admin','site-admin','admin@artistikcity.com','$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e','admin','Pune, India',NULL,NULL,NULL,'Administrator','Administrator','Platform administrator.',NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,'Vaishali J Goswami','vaishali-j-goswami','vaishali@artistikcity.com','$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e','teacher','Pune, India','instructor.jpg',NULL,NULL,'Founder & Lead Instructor','Artist, Illustrator & Art Educator','Vaishali is a professional artist with more than 15 years of experience teaching drawing, watercolour and acrylic painting to learners of every age. Her classes focus on building strong fundamentals and helping every student discover a personal style.',NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(3,'Studio Moderator','studio-moderator','moderator@artistikcity.com','$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e','moderator','Pune, India',NULL,NULL,NULL,'Moderator','Gallery Moderator','Reviews student submissions, gallery and testimonials.',NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(11,'Ananya Rao','ananya-rao','ananya@artistikcity.com','$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e','teacher','Bengaluru, India','avatar.png',NULL,NULL,'Watercolour Instructor','Botanical & Watercolour Artist','<p>Ananya paints flowers, leaves and birds in transparent watercolour. She trained in botanical illustration and has taught more than 2,000 students to see colour and light in nature.</p>',NULL,'2026-04-07 14:22:13','2026-10-24 14:22:13'),(12,'Kabir Malhotra','kabir-malhotra','kabir@artistikcity.com','$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e','teacher','New Delhi, India','avatar.png',NULL,NULL,'Charcoal & Drawing Instructor','Portrait Artist','<p>Kabir draws people. His charcoal portraits have been shown in Delhi and Mumbai, and his classes focus on proportion, values and patient observation.</p>',NULL,'2026-04-17 14:22:13','2026-10-24 14:22:13'),(13,'Meera Krishnan','meera-krishnan','meera@artistikcity.com','$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e','teacher','Chennai, India','avatar.png',NULL,NULL,'Acrylic Instructor','Contemporary Painter','<p>Meera works in bold acrylic colour with palette knives and pours. She helps beginners lose their fear of the blank canvas.</p>',NULL,'2026-04-27 14:22:13','2026-10-24 14:22:13'),(14,'Rohit Desai','rohit-desai','rohit@artistikcity.com','$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e','teacher','Mumbai, India','avatar.png',NULL,NULL,'Oil Painting Instructor','Landscape Painter','<p>Rohit paints landscapes en plein air in oils. He teaches the classical process of underpainting, blocking in and glazing step by step.</p>',NULL,'2026-05-07 14:22:13','2026-10-24 14:22:13'),(15,'Sana Qureshi','sana-qureshi','sana@artistikcity.com','$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e','teacher','Hyderabad, India','avatar.png',NULL,NULL,'Digital Art Instructor','Illustrator & Designer','<p>Sana illustrates books and brands on the iPad. Her classes cover Procreate, lettering and ink sketching on location.</p>',NULL,'2026-05-17 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `admins` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `artist_thread_messages`
--

DROP TABLE IF EXISTS `artist_thread_messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `artist_thread_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `thread_id` bigint NOT NULL,
  `sender` varchar(10) NOT NULL,
  `admin_id` bigint DEFAULT NULL,
  `body` text NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `artist_thread_messages_thread_id_foreign` (`thread_id`),
  CONSTRAINT `artist_thread_messages_thread_id_foreign` FOREIGN KEY (`thread_id`) REFERENCES `artist_threads` (`id`) ON DELETE CASCADE,
  CONSTRAINT `artist_thread_messages_chk_1` CHECK ((`sender` in (_utf8mb4'BUYER',_utf8mb4'ARTIST')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `artist_thread_messages`
--

LOCK TABLES `artist_thread_messages` WRITE;
/*!40000 ALTER TABLE `artist_thread_messages` DISABLE KEYS */;
/*!40000 ALTER TABLE `artist_thread_messages` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `artist_threads`
--

DROP TABLE IF EXISTS `artist_threads`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `artist_threads` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `painting_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `owner_key` varchar(80) NOT NULL,
  `buyer_name` varchar(255) NOT NULL,
  `buyer_email` varchar(255) NOT NULL,
  `context` varchar(255) DEFAULT NULL,
  `status` varchar(10) NOT NULL DEFAULT 'OPEN',
  `staff_unread` int NOT NULL DEFAULT '0',
  `buyer_unread` int NOT NULL DEFAULT '0',
  `last_message_at` datetime NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `artist_threads_painting_id_foreign` (`painting_id`),
  KEY `artist_threads_user_id_foreign` (`user_id`),
  KEY `artist_threads_owner_index` (`owner_key`,`painting_id`),
  CONSTRAINT `artist_threads_painting_id_foreign` FOREIGN KEY (`painting_id`) REFERENCES `paintings` (`id`) ON DELETE CASCADE,
  CONSTRAINT `artist_threads_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `artist_threads_chk_1` CHECK ((`status` in (_utf8mb4'OPEN',_utf8mb4'CLOSED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `artist_threads`
--

LOCK TABLES `artist_threads` WRITE;
/*!40000 ALTER TABLE `artist_threads` DISABLE KEYS */;
/*!40000 ALTER TABLE `artist_threads` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_events`
--

DROP TABLE IF EXISTS `auth_events`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `event` varchar(50) NOT NULL,
  `method` varchar(20) DEFAULT NULL,
  `success` tinyint(1) NOT NULL DEFAULT '1',
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `detail` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `auth_events_user_index` (`user_id`,`created_at`),
  KEY `auth_events_event_index` (`event`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_events`
--

LOCK TABLES `auth_events` WRITE;
/*!40000 ALTER TABLE `auth_events` DISABLE KEYS */;
INSERT INTO `auth_events` VALUES (1,1,'login','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36 Edg/155.0.0.0',NULL,'2026-10-24 14:27:08'),(2,1,'login','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36',NULL,'2026-10-24 14:39:41'),(3,1,'login','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36',NULL,'2026-10-25 14:43:19'),(4,1,'login','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36',NULL,'2026-10-25 14:58:48'),(5,1,'login','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36',NULL,'2026-10-25 15:04:01'),(6,1,'login','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36',NULL,'2026-10-25 15:29:20'),(7,1,'login','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0',NULL,'2026-10-25 21:17:30'),(8,1,'login','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0',NULL,'2026-10-25 23:18:28'),(9,1,'login','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36',NULL,'2026-10-25 23:33:58'),(10,1,'login','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0',NULL,'2026-11-05 10:15:34'),(11,NULL,'admin_login_failed','password',0,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','student@artistikcity.com','2026-11-05 10:59:14');
/*!40000 ALTER TABLE `auth_events` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_otps`
--

DROP TABLE IF EXISTS `auth_otps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_otps` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `phone_number` varchar(20) NOT NULL,
  `purpose` varchar(20) NOT NULL DEFAULT 'login',
  `user_id` bigint DEFAULT NULL,
  `code_hash` char(64) NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `expires_at` datetime NOT NULL,
  `consumed_at` datetime DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `auth_otps_phone_index` (`phone_number`,`created_at`),
  KEY `auth_otps_ip_index` (`ip_address`,`created_at`),
  CONSTRAINT `auth_otps_chk_1` CHECK ((`purpose` in (_utf8mb4'login',_utf8mb4'verify_phone')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_otps`
--

LOCK TABLES `auth_otps` WRITE;
/*!40000 ALTER TABLE `auth_otps` DISABLE KEYS */;
/*!40000 ALTER TABLE `auth_otps` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_refresh_tokens`
--

DROP TABLE IF EXISTS `auth_refresh_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_refresh_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `token_hash` char(64) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` datetime NOT NULL,
  `used_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `auth_refresh_tokens_hash_unique` (`token_hash`),
  KEY `auth_refresh_tokens_session_foreign` (`session_id`),
  CONSTRAINT `auth_refresh_tokens_session_foreign` FOREIGN KEY (`session_id`) REFERENCES `auth_sessions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_refresh_tokens`
--

LOCK TABLES `auth_refresh_tokens` WRITE;
/*!40000 ALTER TABLE `auth_refresh_tokens` DISABLE KEYS */;
INSERT INTO `auth_refresh_tokens` VALUES (1,1,'55f837ae99b43ea740029579d397e4a28a9974d116209f9a8c894f2cbb9baf75','2026-10-24 14:27:08','2026-11-23 14:27:08','2026-10-24 15:42:44'),(2,2,'049a54d33ce47be0a3a2570c3d37c39766d80de0cb96a5d96d43d7bb54761d49','2026-10-24 14:39:41','2026-11-23 14:39:41','2026-10-24 15:10:52'),(3,2,'7fa9b9bb12eac62cbadec5061d158583acc06b655f36dbfc50585239231e6b4f','2026-10-24 15:10:52','2026-11-23 14:39:41',NULL),(4,1,'fe42e9a965e473acf6d2d964b9818f93662a1999869f3982725390c1f49c5970','2026-10-24 15:42:44','2026-11-23 14:27:08','2026-10-25 11:36:16'),(5,1,'f6b7e410d1f762f739b3bff6c7d795c3be4240d71ca345cdaa204e68bd393807','2026-10-25 11:36:16','2026-11-23 14:27:08','2026-10-25 13:20:51'),(6,1,'0029374d6fb41f69f6afabf88bd287d7a12e59a6fe38f353ba716b542a85fc6e','2026-10-25 13:20:51','2026-11-23 14:27:08',NULL),(7,3,'97d28a6e84083492e1573a6242dbbb93cbbb07b0f05ee98ae8d09d5fd1b62929','2026-10-25 14:43:19','2026-11-24 14:43:19',NULL),(8,4,'d41d2ae9fc18f4f71c6f8a79490dfc29deb8a39abf52f41ddbf5f6d11304ed18','2026-10-25 14:58:48','2026-11-24 14:58:48',NULL),(9,5,'681cc12daa9c1e8caf82cf172ac0ffcb4d669240a3f7e97a3242dc023e02d064','2026-10-25 15:04:01','2026-11-24 15:04:01',NULL),(10,6,'29d1f6df965bdaac226baa2915cee8eb6052baf21e3583a374b87256d8987375','2026-10-25 15:29:20','2026-11-24 15:29:20','2026-10-25 21:57:22'),(11,7,'7360f0c3f86fcc0b11e5db2179e13f2f159c31a664b9cf812e252bb54323f182','2026-10-25 21:17:30','2026-11-24 21:17:30','2026-10-25 23:18:09'),(12,6,'a71d56633ffc60c67474cfee264eb7621629277c67708dd4eb0c105c477b237d','2026-10-25 21:57:22','2026-11-24 15:29:20','2026-10-25 23:26:23'),(13,7,'d6d7a83a98bdac9ecf560ea865f2544aeefc17f1bbc2ca1ae835a0181809b95d','2026-10-25 23:18:09','2026-11-24 21:17:30',NULL),(14,8,'b1fc768ec971e717a03b00c412f9b05e2d43ae62b54f6de1bc4574bd1f631d30','2026-10-25 23:18:28','2026-11-24 23:18:28',NULL),(15,6,'c25458b26560209ff3efbca23cc5921a8c4c287bc4d4a16551b2847f6bf0af08','2026-10-25 23:26:23','2026-11-24 15:29:20',NULL),(16,9,'7167b27445c9c1d782b9dcf28616bdd2c23fe998e32389f83b10143a0feecabb','2026-10-25 23:33:58','2026-11-24 23:33:58','2026-11-05 10:59:09'),(17,10,'71948fc74946017e4929c664562e36754b702c983463cce5e3a62d73d745cdf0','2026-11-05 10:15:34','2026-12-05 10:15:34','2026-11-05 11:54:08'),(18,9,'c17329b110f9058cd57b7cd4ae3640b27bcf478c5a81d71615e98f5c99cd39f3','2026-11-05 10:59:09','2026-11-24 23:33:58',NULL),(19,10,'5ef052a4c15ea3d6deb75b9115b66696c65589630d16d124bb9095bc373a25c7','2026-11-05 11:54:08','2026-12-05 10:15:34',NULL);
/*!40000 ALTER TABLE `auth_refresh_tokens` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_sessions`
--

DROP TABLE IF EXISTS `auth_sessions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `session_token_hash` char(64) NOT NULL,
  `session_expires_at` datetime NOT NULL,
  `previous_token_hash` char(64) DEFAULT NULL,
  `previous_valid_until` datetime DEFAULT NULL,
  `rotated_at` datetime NOT NULL,
  `auth_method` varchar(20) NOT NULL,
  `remember` tinyint(1) NOT NULL DEFAULT '0',
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_seen_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` datetime NOT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `revoke_reason` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `auth_sessions_token_unique` (`session_token_hash`),
  KEY `auth_sessions_user_index` (`user_id`,`revoked_at`),
  KEY `auth_sessions_previous_index` (`previous_token_hash`),
  CONSTRAINT `auth_sessions_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `auth_sessions_chk_1` CHECK ((`auth_method` in (_utf8mb4'password',_utf8mb4'google',_utf8mb4'facebook',_utf8mb4'otp',_utf8mb4'magic_link',_utf8mb4'passkey',_utf8mb4'signup')))
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_sessions`
--

LOCK TABLES `auth_sessions` WRITE;
/*!40000 ALTER TABLE `auth_sessions` DISABLE KEYS */;
INSERT INTO `auth_sessions` VALUES (1,1,'18573b6f3ff20385f99002cc6595ce9d927f11fd157a932f08dcf576cf975f11','2026-10-25 13:50:51',NULL,NULL,'2026-10-25 13:20:51','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0','2026-10-24 14:27:08','2026-10-25 13:20:51','2026-11-23 14:27:08','2026-10-25 13:20:55','logout'),(2,1,'afe127bced6829978ef8aa63fedfc6ec6de9c9f07b45694af60fccf923c5fa55','2026-10-24 15:40:52',NULL,NULL,'2026-10-24 15:10:52','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','2026-10-24 14:39:41','2026-10-24 15:10:52','2026-11-23 14:39:41','2026-10-24 15:11:06','logout'),(3,1,'a3f3c2a1802f5d1fd361b4b9a906a929b3658da1ae1f9940a09ddc8571383895','2026-10-25 15:18:40',NULL,NULL,'2026-10-25 14:43:19','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','2026-10-25 14:43:19','2026-10-25 14:48:40','2026-11-24 14:43:19','2026-10-25 14:49:18','logout'),(4,1,'2b9e142e884a79cc590370951566dfe30b45de5fe1efbb90c32fc5bf20a13a3b','2026-10-25 15:33:52',NULL,NULL,'2026-10-25 14:58:48','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','2026-10-25 14:58:48','2026-10-25 15:03:52','2026-11-24 14:58:48','2026-10-25 15:03:54','logout'),(5,1,'c482ebc92e1a8fafb00f9171fb06370760cc93d1044d18bdaa91cb7be3ce6c8e','2026-10-25 15:41:29',NULL,NULL,'2026-10-25 15:04:01','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','2026-10-25 15:04:01','2026-10-25 15:11:29','2026-11-24 15:04:01','2026-10-25 15:11:58','logout'),(6,1,'7cdc5e254cf2b533788adf1430f40da95cf54196aa42a52ded75db13ca17c477','2026-10-25 23:56:23',NULL,NULL,'2026-10-25 23:26:23','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','2026-10-25 15:29:20','2026-10-25 23:26:23','2026-11-24 15:29:20','2026-10-25 23:26:27','logout'),(7,1,'466775437217da91132e1a42967ed20872627a908ade30672d159f7e3cde7f8c','2026-10-25 23:48:09',NULL,NULL,'2026-10-25 23:18:09','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0','2026-10-25 21:17:30','2026-10-25 23:18:09','2026-11-24 21:17:30','2026-10-25 23:18:23','logout'),(8,1,'ca1d3d666b6cbe9c5856d44ebd9cff2150cfcf7b32a456b70ee0e2cf03f3c9b8','2026-10-26 00:19:26','d2e15e23266ce5c413d54e84f1b9001fb13e87e280890f01e4683e0eaac02628','2026-10-25 23:50:26','2026-10-25 23:49:26','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0','2026-10-25 23:18:28','2026-10-25 23:49:26','2026-11-24 23:18:28',NULL,NULL),(9,1,'2ba7a79e8962b94d10559079fe7058fd44888b5f16dfa26de25bf45ef7b15f4b','2026-11-05 11:48:45','02ae0a520b18c801cd117184c6db6247905c6a61d08e75d9159c8cd59f157f78','2026-11-05 11:16:10','2026-11-05 11:15:10','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36','2026-10-25 23:33:58','2026-11-05 11:18:45','2026-11-24 23:33:58',NULL,NULL),(10,1,'efe4d607e299156a274f99aa9f45fea7fc9c5ee58ccfff2f652effbf5ac0b9ce','2026-11-05 12:27:55',NULL,NULL,'2026-11-05 11:54:08','password',1,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0','2026-11-05 10:15:34','2026-11-05 11:57:55','2026-12-05 10:15:34',NULL,NULL);
/*!40000 ALTER TABLE `auth_sessions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_tokens`
--

DROP TABLE IF EXISTS `auth_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `purpose` varchar(30) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `token_hash` char(64) NOT NULL,
  `expires_at` datetime NOT NULL,
  `consumed_at` datetime DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `meta` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `auth_tokens_hash_unique` (`token_hash`),
  KEY `auth_tokens_user_id_foreign` (`user_id`),
  KEY `auth_tokens_email_index` (`email`,`purpose`,`created_at`),
  CONSTRAINT `auth_tokens_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `auth_tokens_chk_1` CHECK ((`purpose` in (_utf8mb4'magic_link',_utf8mb4'verify_email',_utf8mb4'password_reset')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_tokens`
--

LOCK TABLES `auth_tokens` WRITE;
/*!40000 ALTER TABLE `auth_tokens` DISABLE KEYS */;
/*!40000 ALTER TABLE `auth_tokens` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cart_items`
--

DROP TABLE IF EXISTS `cart_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cart_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(255) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `course_id` bigint NOT NULL,
  `price_type` varchar(10) DEFAULT NULL,
  `price` varchar(50) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cart_items`
--

LOCK TABLES `cart_items` WRITE;
/*!40000 ALTER TABLE `cart_items` DISABLE KEYS */;
/*!40000 ALTER TABLE `cart_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cart_reservations`
--

DROP TABLE IF EXISTS `cart_reservations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cart_reservations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_or_user_id` varchar(255) NOT NULL,
  `painting_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `reserved_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `cart_reservations_painting_unique` (`painting_id`),
  KEY `IX_CartReservations_Expiry` (`expires_at`),
  KEY `cart_reservations_holder_index` (`session_or_user_id`),
  CONSTRAINT `cart_reservations_painting_id_foreign` FOREIGN KEY (`painting_id`) REFERENCES `paintings` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cart_reservations`
--

LOCK TABLES `cart_reservations` WRITE;
/*!40000 ALTER TABLE `cart_reservations` DISABLE KEYS */;
/*!40000 ALTER TABLE `cart_reservations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `categories`
--

DROP TABLE IF EXISTS `categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `slug` varchar(255) NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=103 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `categories`
--

LOCK TABLES `categories` WRITE;
/*!40000 ALTER TABLE `categories` DISABLE KEYS */;
INSERT INTO `categories` VALUES (1,'Art Tips','art-tips',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,'Techniques','techniques',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(3,'Inspiration','inspiration',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(101,'Studio News','studio-news',1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(102,'Student Stories','student-stories',1,'2026-10-24 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `categories` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `category_post`
--

DROP TABLE IF EXISTS `category_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category_post` (
  `category_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  KEY `category_post_category_id_foreign` (`category_id`),
  KEY `category_post_post_id_foreign` (`post_id`),
  CONSTRAINT `category_post_category_id_foreign` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`),
  CONSTRAINT `category_post_post_id_foreign` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `category_post`
--

LOCK TABLES `category_post` WRITE;
/*!40000 ALTER TABLE `category_post` DISABLE KEYS */;
INSERT INTO `category_post` VALUES (1,1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,2,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(3,3,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(1,101,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(2,101,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(2,102,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(2,103,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(102,104,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(101,105,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1,106,'2026-10-24 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `category_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `certificates`
--

DROP TABLE IF EXISTS `certificates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `certificates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `admin_id` bigint NOT NULL,
  `user_name` varchar(255) DEFAULT NULL,
  `course_title` varchar(255) DEFAULT NULL,
  `teacher_name` varchar(255) DEFAULT NULL,
  `certificate_number` varchar(255) DEFAULT NULL,
  `certificate_name` varchar(500) DEFAULT NULL,
  `certificate_path` varchar(1024) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `certificates_user_id_foreign` (`user_id`),
  KEY `certificates_course_id_foreign` (`course_id`),
  KEY `certificates_order_id_foreign` (`order_id`),
  KEY `certificates_admin_id_foreign` (`admin_id`),
  CONSTRAINT `certificates_admin_id_foreign` FOREIGN KEY (`admin_id`) REFERENCES `admins` (`id`),
  CONSTRAINT `certificates_course_id_foreign` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `certificates_order_id_foreign` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `certificates_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `certificates`
--

LOCK TABLES `certificates` WRITE;
/*!40000 ALTER TABLE `certificates` DISABLE KEYS */;
/*!40000 ALTER TABLE `certificates` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `commission_artwork_items`
--

DROP TABLE IF EXISTS `commission_artwork_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commission_artwork_items` (
  `item_id` varchar(50) NOT NULL,
  `order_id` varchar(50) NOT NULL,
  `artwork_theme` varchar(50) NOT NULL,
  `width_inches` int NOT NULL,
  `height_inches` int NOT NULL,
  `frame_material` varchar(100) NOT NULL DEFAULT 'NONE',
  `has_matting` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `commission_artwork_items_order_id_foreign` (`order_id`),
  CONSTRAINT `commission_artwork_items_order_id_foreign` FOREIGN KEY (`order_id`) REFERENCES `commission_orders` (`order_id`) ON DELETE CASCADE,
  CONSTRAINT `commission_artwork_items_chk_1` CHECK ((`artwork_theme` in (_utf8mb4'PORTRAIT',_utf8mb4'LANDSCAPE',_utf8mb4'WATERCOLOR',_utf8mb4'CHARCOAL')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `commission_artwork_items`
--

LOCK TABLES `commission_artwork_items` WRITE;
/*!40000 ALTER TABLE `commission_artwork_items` DISABLE KEYS */;
INSERT INTO `commission_artwork_items` VALUES ('AC-C-DEMO101-1','AC-C-DEMO101','LANDSCAPE',16,20,'Teak',1),('AC-C-DEMO102-1','AC-C-DEMO102','CHARCOAL',12,16,'Black',0),('AC-C-DEMO103-1','AC-C-DEMO103','WATERCOLOR',11,14,'Oak',1),('AC-C-DEMO104-1','AC-C-DEMO104','PORTRAIT',8,10,'NONE',0),('AC-C-DEMO105-1','AC-C-DEMO105','LANDSCAPE',18,24,'Teak',1),('ITEM-746D8B02-CAA1-4A54','AC-20261025-UJLMUA','PORTRAIT',8,10,'NONE',0);
/*!40000 ALTER TABLE `commission_artwork_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `commission_orders`
--

DROP TABLE IF EXISTS `commission_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commission_orders` (
  `order_id` varchar(50) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `customer_name` varchar(255) NOT NULL,
  `customer_email` varchar(255) NOT NULL,
  `customer_phone` varchar(50) DEFAULT NULL,
  `fulfillment_type` varchar(50) NOT NULL,
  `special_instructions` longtext,
  `uploaded_photo_url` varchar(2083) NOT NULL,
  `shipping_address_line1` varchar(255) DEFAULT NULL,
  `shipping_city` varchar(100) DEFAULT NULL,
  `shipping_postal_code` varchar(20) DEFAULT NULL,
  `shipping_country` varchar(100) DEFAULT NULL,
  `payment_method` varchar(50) NOT NULL,
  `payment_provider_ref` varchar(255) DEFAULT NULL,
  `payment_transaction_id` varchar(255) DEFAULT NULL,
  `order_status` varchar(50) NOT NULL DEFAULT 'PAYMENT_PENDING',
  `currency` varchar(3) NOT NULL DEFAULT 'USD',
  `total_price` decimal(10,2) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`order_id`),
  KEY `commission_orders_provider_ref_index` (`payment_provider_ref`),
  CONSTRAINT `commission_orders_chk_1` CHECK ((`fulfillment_type` in (_utf8mb4'DIGITAL_ONLY',_utf8mb4'PHYSICAL_PRINT',_utf8mb4'ORIGINAL_PAINTING'))),
  CONSTRAINT `commission_orders_chk_2` CHECK ((`payment_method` in (_utf8mb4'CREDIT_CARD',_utf8mb4'RAZORPAY_UPI',_utf8mb4'PAYPAL'))),
  CONSTRAINT `commission_orders_chk_3` CHECK ((`order_status` in (_utf8mb4'PAYMENT_PENDING',_utf8mb4'QUEUED',_utf8mb4'PROOF_SENT',_utf8mb4'IN_PRODUCTION',_utf8mb4'SHIPPED',_utf8mb4'COMPLETED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `commission_orders`
--

LOCK TABLES `commission_orders` WRITE;
/*!40000 ALTER TABLE `commission_orders` DISABLE KEYS */;
INSERT INTO `commission_orders` VALUES ('AC-20261025-UJLMUA',NULL,'Amit Goswami','mailtoamitgoswami1027@gmail.com','07330994445','DIGITAL_ONLY',NULL,'/storage/uploads/commissions/4e6ed472-1015-4445-96fb-960151eab11d.jpg',NULL,NULL,NULL,NULL,'RAZORPAY_UPI',NULL,'TEST-1792921760817','QUEUED','INR',4120.00,'2026-10-25 15:19:20','2026-10-25 15:19:21'),('AC-C-DEMO101',104,'Sophia Fernandes','sophia@example.com','+919811000104','ORIGINAL_PAINTING','Portrait of our two dogs on the beach at sunset.','/storage/uploads/commissions/72605d47-3131-4fda-a8b3-2da22e672c1e.jpg','Address on file','Panaji','403001','India','RAZORPAY_UPI','order_DEMOC0','pay_DEMOC0','QUEUED','INR',18500.00,'2026-10-18 14:22:13','2026-10-21 14:22:13'),('AC-C-DEMO102',102,'Neha Kulkarni','neha@example.com','+919811000102','ORIGINAL_PAINTING','Charcoal portrait of my parents for their anniversary.','/storage/uploads/commissions/5f3d82dd-6f9b-43f9-b89b-da7cf5bef620.jpg','Address on file','Pune','411004','India','RAZORPAY_UPI','order_DEMOC1','pay_DEMOC1','PROOF_SENT','INR',12000.00,'2026-10-10 14:22:13','2026-10-13 14:22:13'),('AC-C-DEMO103',106,'Emily Clarke','emily@example.com','+447700900106','PHYSICAL_PRINT','Watercolour of our cottage in the Cotswolds.','/storage/uploads/commissions/cb28f7cf-92a6-4fe3-be3b-5ec5169b5f3b.jpg','Address on file','London','SW1A 1AA','United Kingdom','PAYPAL','order_DEMOC2','pay_DEMOC2','IN_PRODUCTION','USD',240.00,'2026-09-29 14:22:13','2026-10-02 14:22:13'),('AC-C-DEMO104',101,'Ishaan Verma','ishaan@example.com','+919811000101','DIGITAL_ONLY','Family portrait for a greeting card.','/storage/uploads/commissions/9a3099a7-20b9-433b-8d68-ff76dbf7d859.jpg','Address on file','Jaipur','302001','India','RAZORPAY_UPI','order_DEMOC3','pay_DEMOC3','SHIPPED','INR',4500.00,'2026-09-14 14:22:13','2026-09-17 14:22:13'),('AC-C-DEMO105',103,'Arjun Menon','arjun@example.com','+919811000103','ORIGINAL_PAINTING','Kerala backwaters at dawn.','/storage/uploads/commissions/4aa162e3-b7bc-4b57-b4f8-a9e6e82a74d2.jpg','Address on file','Kochi','682011','India','RAZORPAY_UPI','order_DEMOC4','pay_DEMOC4','COMPLETED','INR',22000.00,'2026-08-10 14:22:13','2026-08-13 14:22:13');
/*!40000 ALTER TABLE `commission_orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `contacts`
--

DROP TABLE IF EXISTS `contacts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contacts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `mobile_no` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `message` longtext,
  `status` tinyint DEFAULT '1',
  `read_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contacts`
--

LOCK TABLES `contacts` WRITE;
/*!40000 ALTER TABLE `contacts` DISABLE KEYS */;
INSERT INTO `contacts` VALUES (101,'Pooja Shah','9822000001','pooja.shah@example.com','Do you have evening classes for working professionals?',1,NULL,'2026-10-23 14:22:13','2026-10-23 14:22:13'),(102,'Vikram Singh','9822000002','vikram@example.com','Can I gift a course to my daughter for her birthday?',1,NULL,'2026-10-22 14:22:13','2026-10-22 14:22:13'),(103,'Lena Fischer',NULL,'lena@example.com','Do you ship original paintings to Germany? I love the Himalayan Morning painting.',1,NULL,'2026-10-21 14:22:13','2026-10-21 14:22:13'),(104,'Ravi Kumar','9822000004','ravi.k@example.com','We would like a corporate art workshop for 25 people. Please share details.',2,'2026-10-19 02:22:13','2026-10-18 14:22:13','2026-10-18 14:22:13'),(105,'Anjali Das','9822000005','anjali@example.com','Thank you for the wonderful watercolour course!',3,'2026-10-13 02:22:13','2026-10-12 14:22:13','2026-10-12 14:22:13'),(106,'Tom Baker',NULL,'tom@example.com','Is the oil painting course suitable for a complete beginner?',2,'2026-10-16 02:22:13','2026-10-15 14:22:13','2026-10-15 14:22:13');
/*!40000 ALTER TABLE `contacts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_certificates`
--

DROP TABLE IF EXISTS `course_certificates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_certificates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enrollment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `certificate_no` varchar(40) NOT NULL,
  `issued_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `course_certificates_enrollment_unique` (`enrollment_id`),
  UNIQUE KEY `course_certificates_number_unique` (`certificate_no`),
  KEY `course_certificates_user_id_index` (`user_id`),
  KEY `course_certificates_course_id_index` (`course_id`),
  CONSTRAINT `course_certificates_course_id_foreign` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `course_certificates_enrollment_id_foreign` FOREIGN KEY (`enrollment_id`) REFERENCES `enrollments` (`id`),
  CONSTRAINT `course_certificates_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_certificates`
--

LOCK TABLES `course_certificates` WRITE;
/*!40000 ALTER TABLE `course_certificates` DISABLE KEYS */;
INSERT INTO `course_certificates` VALUES (1,1,1,1,'AC-2026-VQ7F382N','2026-11-05 10:21:43');
/*!40000 ALTER TABLE `course_certificates` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_module_lesson_tasks`
--

DROP TABLE IF EXISTS `course_module_lesson_tasks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_module_lesson_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint DEFAULT NULL,
  `course_id` bigint NOT NULL,
  `module_id` bigint NOT NULL,
  `lesson_id` bigint NOT NULL,
  `task_title` varchar(255) NOT NULL,
  `task_description` longtext,
  `status` varchar(20) NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `course_module_lesson_tasks_course_id_foreign` (`course_id`),
  KEY `course_module_lesson_tasks_module_id_foreign` (`module_id`),
  KEY `course_module_lesson_tasks_lesson_id_foreign` (`lesson_id`),
  KEY `course_module_lesson_tasks_admin_id_foreign` (`admin_id`),
  CONSTRAINT `course_module_lesson_tasks_admin_id_foreign` FOREIGN KEY (`admin_id`) REFERENCES `admins` (`id`),
  CONSTRAINT `course_module_lesson_tasks_course_id_foreign` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `course_module_lesson_tasks_lesson_id_foreign` FOREIGN KEY (`lesson_id`) REFERENCES `course_module_lessons` (`id`),
  CONSTRAINT `course_module_lesson_tasks_module_id_foreign` FOREIGN KEY (`module_id`) REFERENCES `course_modules` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12603 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_module_lesson_tasks`
--

LOCK TABLES `course_module_lesson_tasks` WRITE;
/*!40000 ALTER TABLE `course_module_lesson_tasks` DISABLE KEYS */;
INSERT INTO `course_module_lesson_tasks` VALUES (1,2,1,1,2,'Paint three wash swatches','Paint a flat, a graded and a wet-in-wet wash and upload a photo.','1','2026-10-24 13:27:29','2026-10-24 13:27:29'),(10102,11,101,1011,10102,'Practice: contour drawing','Make one study for \"Contour drawing\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10105,11,101,1012,10105,'Practice: greens that sing','Make one study for \"Greens that sing\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10107,11,101,1013,10107,'Practice: finishing details','Make one study for \"Finishing details\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10202,11,102,1021,10202,'Practice: bird anatomy made simple','Make one study for \"Bird anatomy made simple\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10204,11,102,1022,10204,'Practice: bark and rock','Make one study for \"Bark and rock\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10206,11,102,1023,10206,'Practice: the final painting','Make one study for \"The final painting\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10302,12,103,1031,10302,'Practice: placing the features','Make one study for \"Placing the features\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10305,12,103,1032,10305,'Practice: skin texture and age','Make one study for \"Skin texture and age\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10307,12,103,1033,10307,'Practice: final portrait session','Make one study for \"Final portrait session\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10402,12,104,1041,10402,'Practice: lips and nose','Make one study for \"Lips and nose\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10404,12,104,1042,10404,'Practice: curls and movement','Make one study for \"Curls and movement\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10405,12,104,1043,10405,'Practice: full face study','Make one study for \"Full face study\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10502,2,105,1051,10502,'Practice: line games','Make one study for \"Line games\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10504,2,105,1052,10504,'Practice: textures','Make one study for \"Textures\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10505,2,105,1053,10505,'Practice: drawing a parrot','Make one study for \"Drawing a parrot\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10602,13,106,1061,10602,'Practice: colour harmony','Make one study for \"Colour harmony\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10603,13,106,1062,10603,'Practice: flip cup pour','Make one study for \"Flip cup pour\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10605,13,106,1063,10605,'Practice: finishing and varnish','Make one study for \"Finishing and varnish\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10702,13,107,1071,10702,'Practice: clouds','Make one study for \"Clouds\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10704,13,107,1072,10704,'Practice: pine trees','Make one study for \"Pine trees\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10706,13,107,1073,10706,'Practice: final landscape','Make one study for \"Final landscape\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10802,14,108,1081,10802,'Practice: fat over lean','Make one study for \"Fat over lean\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10804,14,108,1082,10804,'Practice: blocking in colour','Make one study for \"Blocking in colour\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10806,14,108,1083,10806,'Practice: final painting','Make one study for \"Final painting\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10902,12,109,1091,10902,'Practice: metal','Make one study for \"Metal\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10904,12,109,1092,10904,'Practice: final still life','Make one study for \"Final still life\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11002,15,110,1101,11002,'Practice: gestures and shortcuts','Make one study for \"Gestures and shortcuts\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11004,15,110,1102,11004,'Practice: lighting a scene','Make one study for \"Lighting a scene\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11006,15,110,1103,11006,'Practice: final illustration','Make one study for \"Final illustration\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11102,15,111,1111,11102,'Practice: simple perspective','Make one study for \"Simple perspective\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11104,15,111,1112,11104,'Practice: a café scene','Make one study for \"A café scene\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11202,2,112,1121,11202,'Practice: drawing a realistic eye','Make one study for \"Drawing a realistic eye\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11204,2,112,1122,11204,'Practice: lips','Make one study for \"Lips\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11301,15,113,1131,11301,'Practice: collage backgrounds','Make one study for \"Collage backgrounds\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11302,15,113,1132,11302,'Practice: stencils and stamps','Make one study for \"Stencils and stamps\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12102,11,121,1211,12102,'Practice: a bouquet of roses','Make one study for \"A bouquet of roses\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12202,12,122,1221,12202,'Practice: your pet portrait','Make one study for \"Your pet portrait\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12302,13,123,1231,12302,'Practice: sunset over the hills','Make one study for \"Sunset over the hills\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12402,12,124,1241,12402,'Practice: a glass bottle','Make one study for \"A glass bottle\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12501,2,125,1251,12501,'Practice: colourful fruit','Make one study for \"Colourful fruit\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12502,2,125,1252,12502,'Practice: paint a parrot','Make one study for \"Paint a parrot\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12602,15,126,1261,12602,'Practice: a lettered quote','Make one study for \"A lettered quote\" and upload a photo. Tell us what was hard.','1','2026-10-24 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `course_module_lesson_tasks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_module_lessons`
--

DROP TABLE IF EXISTS `course_module_lessons`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_module_lessons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint DEFAULT NULL,
  `course_id` bigint NOT NULL,
  `module_id` bigint NOT NULL,
  `lesson_title` varchar(255) NOT NULL,
  `lesson_description` longtext,
  `lesson_pdf` varchar(500) DEFAULT NULL,
  `video_url` varchar(1000) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `course_module_lessons_course_id_foreign` (`course_id`),
  KEY `course_module_lessons_module_id_foreign` (`module_id`),
  KEY `course_module_lessons_admin_id_foreign` (`admin_id`),
  CONSTRAINT `course_module_lessons_admin_id_foreign` FOREIGN KEY (`admin_id`) REFERENCES `admins` (`id`),
  CONSTRAINT `course_module_lessons_course_id_foreign` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `course_module_lessons_module_id_foreign` FOREIGN KEY (`module_id`) REFERENCES `course_modules` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12603 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_module_lessons`
--

LOCK TABLES `course_module_lessons` WRITE;
/*!40000 ALTER TABLE `course_module_lessons` DISABLE KEYS */;
INSERT INTO `course_module_lessons` VALUES (1,2,1,1,'Materials and Set-up','<p>Choosing paper, brushes and paints. Setting up your workspace.</p>',NULL,NULL,0,'1','2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,2,1,1,'Basic Washes','<p>Flat, graded and wet-in-wet washes.</p>',NULL,NULL,0,'1','2026-10-24 13:27:29','2026-10-24 13:27:29'),(3,2,1,2,'Sunset Skies','<p>Blending warm colours for dramatic skies.</p>',NULL,NULL,0,'1','2026-10-24 13:27:29','2026-10-24 13:27:29'),(10101,11,101,1011,'Observing structure','<p>Petals, veins and the geometry hidden in every flower.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10102,11,101,1011,'Contour drawing','<p>Light graphite outlines you can paint over.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10103,11,101,1012,'Wet-on-dry glazes','<p>Build colour in transparent layers without mud.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10104,11,101,1012,'Soft and lost edges','<p>When to blend and when to keep an edge crisp.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10105,11,101,1012,'Greens that sing','<p>Mixing natural greens from blue and yellow.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,3,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10106,11,101,1013,'Composition','<p>Arranging a stem, leaves and a bud on the page.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10107,11,101,1013,'Finishing details','<p>Dry-brush texture and final highlights.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10201,11,102,1021,'Quick gesture sketches','<p>Capture posture in 60 seconds.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10202,11,102,1021,'Bird anatomy made simple','<p>Wings, tail and the line of the back.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10203,11,102,1022,'Feathers','<p>Wet-in-wet bases and dry-brush details.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10204,11,102,1022,'Bark and rock','<p>Salt, spatter and lifting.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10205,11,102,1023,'Planning values','<p>A three-value study first.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10206,11,102,1023,'The final painting','<p>Bringing it together step by step.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10301,12,103,1031,'The Loomis method','<p>Construct the head from a ball and a plane.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10302,12,103,1031,'Placing the features','<p>Eyes, nose and mouth in the right place.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10303,12,103,1032,'Five values of light','<p>Highlight, light, half-tone, core shadow, reflected light.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10304,12,103,1032,'Subtractive drawing','<p>Lifting light out of a toned ground.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10305,12,103,1032,'Skin texture and age','<p>Wrinkles, pores and soft transitions.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,3,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10306,12,103,1033,'Working from a photo','<p>Choosing and adjusting a reference.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10307,12,103,1033,'Final portrait session','<p>A full portrait in four sittings.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10401,12,104,1041,'Eyes','<p>Iris, lids and lashes that look alive.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10402,12,104,1041,'Lips and nose','<p>Soft forms without hard outlines.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10403,12,104,1042,'Shapes before strands','<p>Group hair into masses of value.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10404,12,104,1042,'Curls and movement','<p>Directional strokes and highlights.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10405,12,104,1043,'Full face study','<p>A complete portrait from a reference photo.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10501,2,105,1051,'Drawing with shapes','<p>Turn circles and squares into animals.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10502,2,105,1051,'Line games','<p>Thick, thin, wavy and zig-zag lines.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10503,2,105,1052,'Light and shadow','<p>Make a ball look round.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10504,2,105,1052,'Textures','<p>Fur, feathers and scales.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10505,2,105,1053,'Drawing a parrot','<p>A colourful parrot step by step.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10601,13,106,1061,'Paints, mediums and knives','<p>What to buy and what to skip.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10602,13,106,1061,'Colour harmony','<p>Analogous and complementary palettes.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10603,13,106,1062,'Flip cup pour','<p>Consistency, cells and timing.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10604,13,106,1063,'Scraping and layering','<p>Texture you can feel.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10605,13,106,1063,'Finishing and varnish','<p>Protect and present your piece.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10701,13,107,1071,'Gradient skies','<p>Smooth blends while the paint is wet.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10702,13,107,1071,'Clouds','<p>Soft edges with a dry brush.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10703,13,107,1072,'Atmospheric perspective','<p>Cooler and lighter as things recede.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10704,13,107,1072,'Pine trees','<p>Quick fan-brush trees.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10705,13,107,1073,'Reflections','<p>Vertical strokes and horizontal highlights.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10706,13,107,1073,'Final landscape','<p>Mountains, forest and river.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10801,14,108,1081,'Solvent-free oil painting','<p>A safe set-up for home.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10802,14,108,1081,'Fat over lean','<p>Why layer order matters.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10803,14,108,1082,'Tonal underpainting','<p>Burnt umber value study.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10804,14,108,1082,'Blocking in colour','<p>Big shapes first.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10805,14,108,1083,'Glazes','<p>Deep, jewel-like colour.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10806,14,108,1083,'Final painting','<p>A forest mill landscape.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10901,12,109,1091,'Glass','<p>Edges, refraction and highlights.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10902,12,109,1091,'Metal','<p>High contrast and distorted reflections.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10903,12,109,1092,'Arranging objects','<p>Overlaps and negative space.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(10904,12,109,1092,'Final still life','<p>Kettle, jar and marble.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11001,15,110,1101,'Canvas, brushes and layers','<p>The essential tools.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,'https://www.youtube.com/watch?v=YragxAs9yHY',1,'1','2026-10-24 14:22:13','2026-10-24 14:39:25'),(11002,15,110,1101,'Gestures and shortcuts','<p>Work faster with two fingers.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11003,15,110,1102,'Clipping masks and alpha lock','<p>Shade inside the lines.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11004,15,110,1102,'Lighting a scene','<p>Warm light, cool shadows.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11005,15,110,1103,'Sketch to line art','<p>Clean lines over a loose sketch.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11006,15,110,1103,'Final illustration','<p>Colour, texture and export.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11101,15,111,1111,'Continuous line','<p>Draw without lifting the pen.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11102,15,111,1111,'Simple perspective','<p>One- and two-point street scenes.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11103,15,111,1112,'Sketching people','<p>Quick figures that bring a scene to life.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11104,15,111,1112,'A café scene','<p>A full page in one sitting.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11201,2,112,1121,'Eye structure','<p>The eyeball under the lids.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11202,2,112,1121,'Drawing a realistic eye','<p>Step by step with highlights.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11203,2,112,1122,'The nose','<p>Planes and soft shadows.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11204,2,112,1122,'Lips','<p>Form, texture and light.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11301,15,113,1131,'Collage backgrounds','<p>Paper, tape and gesso.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(11302,15,113,1132,'Stencils and stamps','<p>Repeating patterns.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12101,11,121,1211,'Brush strokes for petals','<p>Press and lift for natural petals.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12102,11,121,1211,'A bouquet of roses','<p>A finished painting in one sitting.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12201,12,122,1221,'Fur directions','<p>Map the fur before drawing it.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12202,12,122,1221,'Your pet portrait','<p>From your own photo.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12301,13,123,1231,'Knife techniques','<p>Spreading, scraping and dabbing.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12302,13,123,1231,'Sunset over the hills','<p>The full painting.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12401,12,124,1241,'Transparency','<p>Seeing through the object.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12402,12,124,1241,'A glass bottle','<p>One finished drawing.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12501,2,125,1251,'Colourful fruit','<p>Oil pastels and blending.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12502,2,125,1252,'Paint a parrot','<p>Bright watercolours.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12601,15,126,1261,'Pressure and strokes','<p>Thin up, thick down.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(12602,15,126,1261,'A lettered quote','<p>Compose and decorate a short quote.</p><h3>In this lesson</h3><ul><li>Live demonstration</li><li>Guided practice</li><li>Questions and feedback</li></ul><blockquote>Tip: work small and often. Ten minutes a day beats one long session a week.</blockquote>',NULL,NULL,2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `course_module_lessons` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_modules`
--

DROP TABLE IF EXISTS `course_modules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_modules` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint DEFAULT NULL,
  `course_id` bigint NOT NULL,
  `module_title` varchar(255) NOT NULL,
  `module_duration` varchar(255) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `course_modules_course_id_foreign` (`course_id`),
  KEY `course_modules_admin_id_foreign` (`admin_id`),
  CONSTRAINT `course_modules_admin_id_foreign` FOREIGN KEY (`admin_id`) REFERENCES `admins` (`id`),
  CONSTRAINT `course_modules_course_id_foreign` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1262 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_modules`
--

LOCK TABLES `course_modules` WRITE;
/*!40000 ALTER TABLE `course_modules` DISABLE KEYS */;
INSERT INTO `course_modules` VALUES (1,2,1,'Getting Started with Watercolour','1 week',0,'1','2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,2,1,'Painting Skies and Trees','2 weeks',0,'1','2026-10-24 13:27:29','2026-10-24 13:27:29'),(1011,11,101,'Seeing like a botanist','1 week',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1012,11,101,'Layering and glazing','2 weeks',2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1013,11,101,'Final botanical plate','3 weeks',3,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1021,11,102,'Gesture and anatomy','2 weeks',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1022,11,102,'Texture','2 weeks',2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1023,11,102,'Portrait of a bird','2 weeks',3,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1031,12,103,'Proportions of the head','2 weeks',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1032,12,103,'Light and value','3 weeks',2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1033,12,103,'The finished portrait','3 weeks',3,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1041,12,104,'Features one by one','2 weeks',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1042,12,104,'Hair','2 weeks',2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1043,12,104,'Putting it together','2 weeks',3,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1051,2,105,'Lines and shapes','1 week',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1052,2,105,'Shading fun','2 weeks',2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1053,2,105,'My first masterpiece','1 week',3,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1061,13,106,'Materials and colour','1 week',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1062,13,106,'Pouring','1 week',2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1063,13,106,'Palette knife','2 weeks',3,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1071,13,107,'Skies','1 week',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1072,13,107,'Mountains and trees','2 weeks',2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1073,13,107,'Water','2 weeks',3,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1081,14,108,'Studio set-up and safety','1 week',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1082,14,108,'Underpainting','3 weeks',2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1083,14,108,'Glazing and details','4 weeks',3,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1091,12,109,'Seeing reflections','2 weeks',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1092,12,109,'Composition','2 weeks',2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1101,15,110,'Getting to know Procreate','1 week',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1102,15,110,'Colour and light','2 weeks',2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1103,15,110,'Your first illustration','3 weeks',3,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1111,15,111,'Line confidence','2 weeks',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1112,15,111,'On location','2 weeks',2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1121,2,112,'The eye','1 week',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1122,2,112,'Nose and lips','2 weeks',2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1131,15,113,'Building pages','2 weeks',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1132,15,113,'Adding layers','2 weeks',2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1211,11,121,'Workshop','1 day',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1221,12,122,'Workshop','3 hours',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1231,13,123,'Workshop','2 sessions',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1241,12,124,'Workshop','3 hours',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1251,2,125,'Day 1','Saturday',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1252,2,125,'Day 2','Sunday',2,'1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1261,15,126,'Workshop','3 hours',1,'1','2026-10-24 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `course_modules` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_photos`
--

DROP TABLE IF EXISTS `course_photos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_photos` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `photo_name` varchar(500) NOT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `course_photos_course_id_foreign` (`course_id`),
  CONSTRAINT `course_photos_course_id_foreign` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1127 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_photos`
--

LOCK TABLES `course_photos` WRITE;
/*!40000 ALTER TABLE `course_photos` DISABLE KEYS */;
INSERT INTO `course_photos` VALUES (1,1,'course.jpg','2026-10-24 13:27:29','2026-10-24 13:27:29'),(3,3,'course.jpg','2026-10-24 13:27:29','2026-10-24 13:27:29'),(4,4,'course.jpg','2026-10-24 13:27:29','2026-10-24 13:27:29'),(1101,101,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1102,102,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1103,103,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1104,104,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1105,105,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1106,106,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1107,107,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1108,108,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1109,109,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1110,110,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1111,111,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1112,112,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1113,113,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1121,121,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1122,122,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1123,123,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1124,124,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1125,125,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1126,126,'cover.jpg','2026-10-24 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `course_photos` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_prices`
--

DROP TABLE IF EXISTS `course_prices`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_prices` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `price_inr` double NOT NULL DEFAULT '0',
  `price_usd` double NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `course_prices_course_id_foreign` (`course_id`),
  CONSTRAINT `course_prices_course_id_foreign` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1127 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_prices`
--

LOCK TABLES `course_prices` WRITE;
/*!40000 ALTER TABLE `course_prices` DISABLE KEYS */;
INSERT INTO `course_prices` VALUES (1,1,4999,69,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(3,3,2999,39,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(4,4,1499,25,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(1101,101,5999,79,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1102,102,6499,85,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1103,103,8999,119,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1104,104,5499,72,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1105,105,2499,35,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1106,106,4999,65,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1107,107,4499,59,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1108,108,7999,105,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1109,109,3999,52,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1110,110,6999,89,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1111,111,3499,45,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1112,112,1999,29,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1113,113,2999,39,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1121,121,999,15,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1122,122,1299,19,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1123,123,1199,17,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1124,124,899,12,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1125,125,1499,22,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(1126,126,999,15,'2026-10-24 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `course_prices` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_projects`
--

DROP TABLE IF EXISTS `course_projects`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_projects` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint DEFAULT NULL,
  `course_id` bigint NOT NULL,
  `project_title` varchar(255) NOT NULL,
  `project_description` longtext,
  `status` varchar(20) NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `course_projects_course_id_foreign` (`course_id`),
  KEY `course_projects_admin_id_foreign` (`admin_id`),
  CONSTRAINT `course_projects_admin_id_foreign` FOREIGN KEY (`admin_id`) REFERENCES `admins` (`id`),
  CONSTRAINT `course_projects_course_id_foreign` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1127 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_projects`
--

LOCK TABLES `course_projects` WRITE;
/*!40000 ALTER TABLE `course_projects` DISABLE KEYS */;
INSERT INTO `course_projects` VALUES (1,2,1,'Final Landscape','Paint a complete landscape using everything you learned.','1','2026-10-24 13:27:29','2026-10-24 13:27:29'),(1101,11,101,'Final project: Finishing details','<p>Complete \"Finishing details\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1102,11,102,'Final project: The final painting','<p>Complete \"The final painting\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1103,12,103,'Final project: Final portrait session','<p>Complete \"Final portrait session\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1104,12,104,'Final project: Full face study','<p>Complete \"Full face study\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1105,2,105,'Final project: Drawing a parrot','<p>Complete \"Drawing a parrot\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1106,13,106,'Final project: Finishing and varnish','<p>Complete \"Finishing and varnish\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1107,13,107,'Final project: Final landscape','<p>Complete \"Final landscape\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1108,14,108,'Final project: Final painting','<p>Complete \"Final painting\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1109,12,109,'Final project: Final still life','<p>Complete \"Final still life\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1110,15,110,'Final project: Final illustration','<p>Complete \"Final illustration\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1111,15,111,'Final project: A café scene','<p>Complete \"A café scene\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1112,2,112,'Final project: Lips','<p>Complete \"Lips\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1113,15,113,'Final project: Stencils and stamps','<p>Complete \"Stencils and stamps\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1121,11,121,'Final project: A bouquet of roses','<p>Complete \"A bouquet of roses\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1122,12,122,'Final project: Your pet portrait','<p>Complete \"Your pet portrait\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1123,13,123,'Final project: Sunset over the hills','<p>Complete \"Sunset over the hills\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1124,12,124,'Final project: A glass bottle','<p>Complete \"A glass bottle\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1125,2,125,'Final project: Paint a parrot','<p>Complete \"Paint a parrot\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13'),(1126,15,126,'Final project: A lettered quote','<p>Complete \"A lettered quote\" using everything from the course and submit it for review. Approved work earns your certificate and can be listed in the marketplace.</p>','1','2026-10-24 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `course_projects` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_reviews`
--

DROP TABLE IF EXISTS `course_reviews`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_reviews` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `added_by_id` bigint NOT NULL,
  `added_by_type` varchar(20) NOT NULL,
  `rating` int DEFAULT NULL,
  `review` longtext,
  `status` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `course_reviews_chk_1` CHECK ((`added_by_type` in (_utf8mb4'Teacher',_utf8mb4'Student')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_reviews`
--

LOCK TABLES `course_reviews` WRITE;
/*!40000 ALTER TABLE `course_reviews` DISABLE KEYS */;
/*!40000 ALTER TABLE `course_reviews` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_types`
--

DROP TABLE IF EXISTS `course_types`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_types` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `course_type_slug` varchar(100) NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_types`
--

LOCK TABLES `course_types` WRITE;
/*!40000 ALTER TABLE `course_types` DISABLE KEYS */;
INSERT INTO `course_types` VALUES (1,'Course','course',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,'Workshop','workshop',1,'2026-10-24 13:27:29','2026-10-24 13:27:29');
/*!40000 ALTER TABLE `course_types` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_videos`
--

DROP TABLE IF EXISTS `course_videos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_videos` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `video_title` varchar(255) DEFAULT NULL,
  `video_url` varchar(1000) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `course_videos_course_id_foreign` (`course_id`),
  CONSTRAINT `course_videos_course_id_foreign` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_videos`
--

LOCK TABLES `course_videos` WRITE;
/*!40000 ALTER TABLE `course_videos` DISABLE KEYS */;
/*!40000 ALTER TABLE `course_videos` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `courses`
--

DROP TABLE IF EXISTS `courses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `courses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `main_course_id` bigint DEFAULT NULL,
  `admin_id` bigint NOT NULL,
  `medium_id` bigint DEFAULT NULL,
  `genre_id` varchar(100) DEFAULT NULL,
  `course_type_id` bigint DEFAULT NULL,
  `skill_id` bigint DEFAULT NULL,
  `category_id` bigint DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `slug` varchar(255) NOT NULL,
  `sub_title` varchar(500) DEFAULT NULL,
  `introduction` longtext,
  `introduction_details` longtext,
  `summary` longtext,
  `summary_details` longtext,
  `schedule` longtext,
  `schedule_details` longtext,
  `schedule_pdf` varchar(500) DEFAULT NULL,
  `age_group` varchar(255) DEFAULT NULL,
  `duration` varchar(255) DEFAULT NULL,
  `sessions` varchar(255) DEFAULT NULL,
  `mini_projects` varchar(255) DEFAULT NULL,
  `course_modules` varchar(255) DEFAULT NULL,
  `time_required` varchar(255) DEFAULT NULL,
  `includes` longtext,
  `course_includes` longtext,
  `course_highlights` longtext,
  `course_for` longtext,
  `course_not_for` longtext,
  `course_problems_solved` longtext,
  `course_learn` longtext,
  `course_outcome` longtext,
  `course_deliverables` longtext,
  `course_prerequisites` longtext,
  `course_start_date` date DEFAULT NULL,
  `course_based` varchar(50) DEFAULT NULL,
  `intro_video_url` varchar(1000) DEFAULT NULL,
  `is_free_course` tinyint NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `courses_admin_id_foreign` (`admin_id`),
  KEY `courses_slug_index` (`slug`),
  CONSTRAINT `courses_admin_id_foreign` FOREIGN KEY (`admin_id`) REFERENCES `admins` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=127 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `courses`
--

LOCK TABLES `courses` WRITE;
/*!40000 ALTER TABLE `courses` DISABLE KEYS */;
INSERT INTO `courses` VALUES (1,NULL,2,1,'1',1,1,1,'Watercolour Landscapes for Beginners','watercolour-landscapes-for-beginners','Learn washes, layering and colour mixing to paint glowing landscapes.','A step-by-step introduction to watercolour painting.','<p>Over four weeks you will learn how to control water and pigment, plan a painting and finish three complete landscapes.</p>','Fundamentals of watercolour, from materials to finished paintings.','<ul><li>Materials and set-up</li><li>Flat, graded and wet-in-wet washes</li><li>Skies, trees and water</li></ul>','Live sessions twice a week.','<p>Week 1: Materials &amp; washes<br>Week 2: Colour mixing<br>Week 3: Skies &amp; trees<br>Week 4: Final landscape</p>',NULL,'12+ years','4','8','3','4','2',NULL,'{\"certificate\":\"yes\",\"study_material_access\":\"yes\"}','[\"Live classes\",\"Personal feedback\"]','[\"Beginners\"]','[\"Professional artists\"]','[\"Muddy colours\"]','[\"Washes\",\"Layering\"]','Three finished watercolour landscapes.','[\"Certificate\",\"Study material\"]','No prior experience needed.','2026-11-23','instructor_based',NULL,0,0,'2026-10-24 13:27:29','2026-10-25 15:27:39'),(3,NULL,2,3,'3',1,1,1,'Pencil Sketching Essentials','pencil-sketching-essentials','Build confident drawing skills with graphite pencils.','Drawing fundamentals for everyone.','<p>Lines, shapes, shading and perspective explained simply.</p>','Everything you need to start sketching.','<ul><li>Line control</li><li>Shading</li><li>Perspective</li></ul>','Self paced with weekly live Q&amp;A.','<p>Four modules, one live Q&amp;A each week.</p>',NULL,'8+ years','4','6','4','4','1',NULL,'{\"certificate\":\"yes\",\"study_material_access\":\"no\"}','[\"Self paced\"]','[\"Kids and adults\"]','[\"Experts\"]','[\"Stiff lines\"]','[\"Shading\",\"Perspective\"]','A sketchbook of finished studies.','[\"Certificate\"]','None.','2026-12-23','instructor_based',NULL,0,0,'2026-10-24 13:27:29','2026-10-25 15:27:38'),(4,NULL,2,1,'1,3',2,1,3,'Weekend Watercolour Florals Workshop','weekend-watercolour-florals-workshop','A relaxing two-day workshop painting loose florals.','Loose florals in a weekend.','<p>Paint roses, peonies and leaves with a loose, joyful style.</p>','Two days of guided painting.','<ul><li>Brush strokes</li><li>Colour harmony</li></ul>','Saturday and Sunday, 3 hours each.','<p>Day 1: Strokes &amp; leaves<br>Day 2: Full floral composition</p>',NULL,'10+ years','2','2','1','2','3',NULL,'{\"certificate\":\"no\",\"study_material_access\":\"yes\"}','[\"Live workshop\"]','[\"Everyone\"]','[\"-\"]','[\"Stiff florals\"]','[\"Loose brushwork\"]','One framed floral painting.','[\"Study material\"]','None.','2026-11-13','instructor_based',NULL,0,1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(101,NULL,11,1,'102',1,2,NULL,'Botanical Watercolour Masterclass','botanical-watercolour-masterclass','Paint flowers and leaves with luminous, layered washes.','Paint flowers and leaves with luminous, layered washes.','<p>Paint flowers and leaves with luminous, layered washes. This course is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','3 modules, 7 lessons and a final project.','<ul><li><strong>Seeing like a botanist</strong>: Observing structure, Contour drawing</li><li><strong>Layering and glazing</strong>: Wet-on-dry glazes, Soft and lost edges, Greens that sing</li><li><strong>Final botanical plate</strong>: Composition, Finishing details</li></ul>','Saturday & Sunday, 11:00 am - 1:00 pm IST','<p>1 week: Seeing like a botanist<br>2 weeks: Layering and glazing<br>3 weeks: Final botanical plate</p>',NULL,'14+ years','6','12','3','3','2',NULL,'{\"certificate\": \"yes\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\", \"Certificate\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Observing structure\", \"Contour drawing\", \"Wet-on-dry glazes\", \"Soft and lost edges\", \"Greens that sing\", \"Composition\"]','<p>You finish with a portfolio piece: finishing details.</p>','[\"Finished artworks\", \"Study notes\", \"Certificate\"]','<p>Comfortable with basic drawing.</p>','2026-11-18','instructor_based',NULL,0,0,'2026-05-11 14:22:13','2026-10-25 15:23:31'),(102,NULL,11,1,'101',1,2,NULL,'Birds & Wildlife in Watercolour','birds-and-wildlife-in-watercolour','From sketch to feathers: paint lively birds and animals.','From sketch to feathers: paint lively birds and animals.','<p>From sketch to feathers: paint lively birds and animals. This course is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','3 modules, 6 lessons and a final project.','<ul><li><strong>Gesture and anatomy</strong>: Quick gesture sketches, Bird anatomy made simple</li><li><strong>Texture</strong>: Feathers, Bark and rock</li><li><strong>Portrait of a bird</strong>: Planning values, The final painting</li></ul>','Saturday & Sunday, 11:00 am - 1:00 pm IST','<p>2 weeks: Gesture and anatomy<br>2 weeks: Texture<br>2 weeks: Portrait of a bird</p>',NULL,'14+ years','6','12','3','3','2',NULL,'{\"certificate\": \"yes\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\", \"Certificate\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Quick gesture sketches\", \"Bird anatomy made simple\", \"Feathers\", \"Bark and rock\", \"Planning values\", \"The final painting\"]','<p>You finish with a portfolio piece: the final painting.</p>','[\"Finished artworks\", \"Study notes\", \"Certificate\"]','<p>Comfortable with basic drawing.</p>','2026-12-03','instructor_based',NULL,0,1,'2026-05-15 14:22:13','2026-10-19 14:22:13'),(103,NULL,12,5,'2',1,3,NULL,'Charcoal Portrait Mastery','charcoal-portrait-mastery','Dramatic, expressive portraits with vine and compressed charcoal.','Dramatic, expressive portraits with vine and compressed charcoal.','<p>Dramatic, expressive portraits with vine and compressed charcoal. This course is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','3 modules, 7 lessons and a final project.','<ul><li><strong>Proportions of the head</strong>: The Loomis method, Placing the features</li><li><strong>Light and value</strong>: Five values of light, Subtractive drawing, Skin texture and age</li><li><strong>The finished portrait</strong>: Working from a photo, Final portrait session</li></ul>','Saturday & Sunday, 11:00 am - 1:00 pm IST','<p>2 weeks: Proportions of the head<br>3 weeks: Light and value<br>3 weeks: The finished portrait</p>',NULL,'16+ years','8','16','3','3','2',NULL,'{\"certificate\": \"yes\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\", \"Certificate\"]','[\"Learners with drawing basics\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"The Loomis method\", \"Placing the features\", \"Five values of light\", \"Subtractive drawing\", \"Skin texture and age\", \"Working from a photo\"]','<p>You finish with a portfolio piece: final portrait session.</p>','[\"Finished artworks\", \"Study notes\", \"Certificate\"]','<p>Comfortable with basic drawing.</p>','2026-11-11','instructor_based',NULL,0,0,'2026-05-19 14:22:13','2026-10-25 15:23:27'),(104,NULL,12,3,'2',1,2,NULL,'Expressive Faces: Graphite Portraits','expressive-faces-graphite-portraits','Draw faces full of character with graphite pencils.','Draw faces full of character with graphite pencils.','<p>Draw faces full of character with graphite pencils. This course is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','3 modules, 5 lessons and a final project.','<ul><li><strong>Features one by one</strong>: Eyes, Lips and nose</li><li><strong>Hair</strong>: Shapes before strands, Curls and movement</li><li><strong>Putting it together</strong>: Full face study</li></ul>','Saturday & Sunday, 11:00 am - 1:00 pm IST','<p>2 weeks: Features one by one<br>2 weeks: Hair<br>2 weeks: Putting it together</p>',NULL,'13+ years','6','10','3','3','2',NULL,'{\"certificate\": \"yes\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\", \"Certificate\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Eyes\", \"Lips and nose\", \"Shapes before strands\", \"Curls and movement\", \"Full face study\"]','<p>You finish with a portfolio piece: full face study.</p>','[\"Finished artworks\", \"Study notes\", \"Certificate\"]','<p>Comfortable with basic drawing.</p>','2026-12-18','instructor_based',NULL,0,0,'2026-05-23 14:22:13','2026-10-25 15:23:25'),(105,NULL,2,3,'3,101',1,1,NULL,'Drawing Fundamentals for Kids (8-12)','drawing-fundamentals-for-kids-8-12','Playful drawing classes that build real skills.','Playful drawing classes that build real skills.','<p>Playful drawing classes that build real skills. This course is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','3 modules, 5 lessons and a final project.','<ul><li><strong>Lines and shapes</strong>: Drawing with shapes, Line games</li><li><strong>Shading fun</strong>: Light and shadow, Textures</li><li><strong>My first masterpiece</strong>: Drawing a parrot</li></ul>','Saturday & Sunday, 11:00 am - 1:00 pm IST','<p>1 week: Lines and shapes<br>2 weeks: Shading fun<br>1 week: My first masterpiece</p>',NULL,'8-12 years','4','8','3','3','2',NULL,'{\"certificate\": \"yes\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\", \"Certificate\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Drawing with shapes\", \"Line games\", \"Light and shadow\", \"Textures\", \"Drawing a parrot\"]','<p>You finish with a portfolio piece: drawing a parrot.</p>','[\"Finished artworks\", \"Study notes\", \"Certificate\"]','<p>None, everything is explained from scratch.</p>','2026-11-05','instructor_based',NULL,0,0,'2026-05-27 14:22:13','2026-10-25 15:23:22'),(106,NULL,13,2,'4',1,1,NULL,'Abstract Acrylic Pouring & Palette Knife','abstract-acrylic-pouring-and-palette-knife','Bold colour, texture and movement for the fearless beginner.','Bold colour, texture and movement for the fearless beginner.','<p>Bold colour, texture and movement for the fearless beginner. This course is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','3 modules, 5 lessons and a final project.','<ul><li><strong>Materials and colour</strong>: Paints, mediums and knives, Colour harmony</li><li><strong>Pouring</strong>: Flip cup pour</li><li><strong>Palette knife</strong>: Scraping and layering, Finishing and varnish</li></ul>','Saturday & Sunday, 11:00 am - 1:00 pm IST','<p>1 week: Materials and colour<br>1 week: Pouring<br>2 weeks: Palette knife</p>',NULL,'14+ years','4','8','3','3','2',NULL,'{\"certificate\": \"yes\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\", \"Certificate\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Paints, mediums and knives\", \"Colour harmony\", \"Flip cup pour\", \"Scraping and layering\", \"Finishing and varnish\"]','<p>You finish with a portfolio piece: finishing and varnish.</p>','[\"Finished artworks\", \"Study notes\", \"Certificate\"]','<p>None, everything is explained from scratch.</p>','2026-11-26','instructor_based',NULL,0,0,'2026-05-31 14:22:13','2026-10-25 15:23:14'),(107,NULL,13,2,'1',1,1,NULL,'Acrylic Landscapes: Mountains & Rivers','acrylic-landscapes-mountains-and-rivers','Paint glowing skies, mountains and water in acrylics.','Paint glowing skies, mountains and water in acrylics.','<p>Paint glowing skies, mountains and water in acrylics. This course is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','3 modules, 6 lessons and a final project.','<ul><li><strong>Skies</strong>: Gradient skies, Clouds</li><li><strong>Mountains and trees</strong>: Atmospheric perspective, Pine trees</li><li><strong>Water</strong>: Reflections, Final landscape</li></ul>','Saturday & Sunday, 11:00 am - 1:00 pm IST','<p>1 week: Skies<br>2 weeks: Mountains and trees<br>2 weeks: Water</p>',NULL,'12+ years','5','10','3','3','2',NULL,'{\"certificate\": \"yes\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\", \"Certificate\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Gradient skies\", \"Clouds\", \"Atmospheric perspective\", \"Pine trees\", \"Reflections\", \"Final landscape\"]','<p>You finish with a portfolio piece: final landscape.</p>','[\"Finished artworks\", \"Study notes\", \"Certificate\"]','<p>None, everything is explained from scratch.</p>','2026-12-23','instructor_based',NULL,0,0,'2026-06-04 14:22:13','2026-10-25 15:27:41'),(108,NULL,14,4,'1,3',1,1,NULL,'Oil Painting Foundations','oil-painting-foundations','The classical oil process from underpainting to glazing.','The classical oil process from underpainting to glazing.','<p>The classical oil process from underpainting to glazing. This course is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','3 modules, 6 lessons and a final project.','<ul><li><strong>Studio set-up and safety</strong>: Solvent-free oil painting, Fat over lean</li><li><strong>Underpainting</strong>: Tonal underpainting, Blocking in colour</li><li><strong>Glazing and details</strong>: Glazes, Final painting</li></ul>','Saturday & Sunday, 11:00 am - 1:00 pm IST','<p>1 week: Studio set-up and safety<br>3 weeks: Underpainting<br>4 weeks: Glazing and details</p>',NULL,'16+ years','8','16','3','3','2',NULL,'{\"certificate\": \"yes\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\", \"Certificate\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Solvent-free oil painting\", \"Fat over lean\", \"Tonal underpainting\", \"Blocking in colour\", \"Glazes\", \"Final painting\"]','<p>You finish with a portfolio piece: final painting.</p>','[\"Finished artworks\", \"Study notes\", \"Certificate\"]','<p>None, everything is explained from scratch.</p>','2026-11-21','instructor_based',NULL,0,0,'2026-06-08 14:22:13','2026-10-25 15:27:34'),(109,NULL,12,3,'3',1,2,NULL,'Still Life Drawing: Glass & Metal','still-life-drawing-glass-and-metal','Master reflections, transparency and shine.','Master reflections, transparency and shine.','<p>Master reflections, transparency and shine. This course is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','2 modules, 4 lessons and a final project.','<ul><li><strong>Seeing reflections</strong>: Glass, Metal</li><li><strong>Composition</strong>: Arranging objects, Final still life</li></ul>','Saturday & Sunday, 11:00 am - 1:00 pm IST','<p>2 weeks: Seeing reflections<br>2 weeks: Composition</p>',NULL,'13+ years','4','8','2','2','2',NULL,'{\"certificate\": \"yes\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\", \"Certificate\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Glass\", \"Metal\", \"Arranging objects\", \"Final still life\"]','<p>You finish with a portfolio piece: final still life.</p>','[\"Finished artworks\", \"Study notes\", \"Certificate\"]','<p>Comfortable with basic drawing.</p>','2027-01-02','instructor_based',NULL,0,0,'2026-06-12 14:22:13','2026-10-25 15:27:42'),(110,NULL,15,6,'4,102',1,1,NULL,'Digital Illustration with Procreate','digital-illustration-with-procreate','Create polished illustrations on the iPad.','Create polished illustrations on the iPad.','<p>Create polished illustrations on the iPad. This course is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','3 modules, 6 lessons and a final project.','<ul><li><strong>Getting to know Procreate</strong>: Canvas, brushes and layers, Gestures and shortcuts</li><li><strong>Colour and light</strong>: Clipping masks and alpha lock, Lighting a scene</li><li><strong>Your first illustration</strong>: Sketch to line art, Final illustration</li></ul>','Saturday & Sunday, 11:00 am - 1:00 pm IST','<p>1 week: Getting to know Procreate<br>2 weeks: Colour and light<br>3 weeks: Your first illustration</p>',NULL,'14+ years','6','12','3','3','2',NULL,'{\"certificate\": \"yes\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\", \"Certificate\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Canvas, brushes and layers\", \"Gestures and shortcuts\", \"Clipping masks and alpha lock\", \"Lighting a scene\", \"Sketch to line art\", \"Final illustration\"]','<p>You finish with a portfolio piece: final illustration.</p>','[\"Finished artworks\", \"Study notes\", \"Certificate\"]','<p>None, everything is explained from scratch.</p>','2026-11-14','instructor_based',NULL,0,1,'2026-06-16 14:22:13','2026-10-25 15:23:05'),(111,NULL,15,101,'103',1,1,NULL,'Ink & Pen Urban Sketching','ink-and-pen-urban-sketching','Sketch streets, cafés and markets in ink.','Sketch streets, cafés and markets in ink.','<p>Sketch streets, cafés and markets in ink. This course is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','2 modules, 4 lessons and a final project.','<ul><li><strong>Line confidence</strong>: Continuous line, Simple perspective</li><li><strong>On location</strong>: Sketching people, A café scene</li></ul>','Saturday & Sunday, 11:00 am - 1:00 pm IST','<p>2 weeks: Line confidence<br>2 weeks: On location</p>',NULL,'14+ years','4','8','2','2','2',NULL,'{\"certificate\": \"yes\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\", \"Certificate\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Continuous line\", \"Simple perspective\", \"Sketching people\", \"A caf\\u00e9 scene\"]','<p>You finish with a portfolio piece: a café scene.</p>','[\"Finished artworks\", \"Study notes\", \"Certificate\"]','<p>None, everything is explained from scratch.</p>','2026-09-14','instructor_based',NULL,0,0,'2026-06-20 14:22:13','2026-10-25 15:23:21'),(112,NULL,2,3,'2',1,1,NULL,'Realistic Eye & Feature Studies','realistic-eye-and-feature-studies','Draw lifelike eyes, noses and lips.','Draw lifelike eyes, noses and lips.','<p>Draw lifelike eyes, noses and lips. This course is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','2 modules, 4 lessons and a final project.','<ul><li><strong>The eye</strong>: Eye structure, Drawing a realistic eye</li><li><strong>Nose and lips</strong>: The nose, Lips</li></ul>','Saturday & Sunday, 11:00 am - 1:00 pm IST','<p>1 week: The eye<br>2 weeks: Nose and lips</p>',NULL,'12+ years','3','6','2','2','2',NULL,'{\"certificate\": \"yes\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\", \"Certificate\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Eye structure\", \"Drawing a realistic eye\", \"The nose\", \"Lips\"]','<p>You finish with a portfolio piece: lips.</p>','[\"Finished artworks\", \"Study notes\", \"Certificate\"]','<p>None, everything is explained from scratch.</p>','2026-08-10','instructor_based',NULL,0,0,'2026-06-24 14:22:13','2026-10-25 15:23:09'),(113,NULL,15,102,'4',1,1,NULL,'Mixed Media Art Journaling','mixed-media-art-journaling','Collage, ink and paint in a creative journal.','Collage, ink and paint in a creative journal.','<p>Collage, ink and paint in a creative journal. This course is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','2 modules, 2 lessons and a final project.','<ul><li><strong>Building pages</strong>: Collage backgrounds</li><li><strong>Adding layers</strong>: Stencils and stamps</li></ul>','Saturday & Sunday, 11:00 am - 1:00 pm IST','<p>2 weeks: Building pages<br>2 weeks: Adding layers</p>',NULL,'12+ years','4','8','2','2','2',NULL,'{\"certificate\": \"yes\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\", \"Certificate\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Collage backgrounds\", \"Stencils and stamps\"]','<p>You finish with a portfolio piece: stencils and stamps.</p>','[\"Finished artworks\", \"Study notes\", \"Certificate\"]','<p>None, everything is explained from scratch.</p>','2027-01-22','instructor_based',NULL,0,0,'2026-06-28 14:22:13','2026-10-25 15:27:36'),(121,NULL,11,1,'102',2,1,NULL,'Loose Florals in One Day','loose-florals-in-one-day','A relaxed one-day workshop painting loose watercolour roses.','A relaxed one-day workshop painting loose watercolour roses.','<p>A relaxed one-day workshop painting loose watercolour roses. This workshop is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','1 modules, 2 lessons and a final project.','<ul><li><strong>Workshop</strong>: Brush strokes for petals, A bouquet of roses</li></ul>','One live session, 3 hours','<p>1 day: Workshop</p>',NULL,'12+ years','1','1','1','1','3',NULL,'{\"certificate\": \"no\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Brush strokes for petals\", \"A bouquet of roses\"]','<p>You finish with a portfolio piece: a bouquet of roses.</p>','[\"Finished artworks\", \"Study notes\"]','<p>None, everything is explained from scratch.</p>','2026-10-30','instructor_based',NULL,0,1,'2026-07-30 14:22:13','2026-10-19 14:22:13'),(122,NULL,12,5,'101,2',2,1,NULL,'Pet Portraits in Charcoal','pet-portraits-in-charcoal','Draw a portrait of your cat or dog in three hours.','Draw a portrait of your cat or dog in three hours.','<p>Draw a portrait of your cat or dog in three hours. This workshop is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','1 modules, 2 lessons and a final project.','<ul><li><strong>Workshop</strong>: Fur directions, Your pet portrait</li></ul>','One live session, 3 hours','<p>3 hours: Workshop</p>',NULL,'12+ years','1','1','1','1','3',NULL,'{\"certificate\": \"no\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Fur directions\", \"Your pet portrait\"]','<p>You finish with a portfolio piece: your pet portrait.</p>','[\"Finished artworks\", \"Study notes\"]','<p>None, everything is explained from scratch.</p>','2026-11-02','instructor_based',NULL,0,1,'2026-08-03 14:22:13','2026-10-19 14:22:13'),(123,NULL,13,2,'1,4',2,1,NULL,'Palette Knife Sunset','palette-knife-sunset','Paint a vibrant sunset with only a palette knife.','Paint a vibrant sunset with only a palette knife.','<p>Paint a vibrant sunset with only a palette knife. This workshop is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','1 modules, 2 lessons and a final project.','<ul><li><strong>Workshop</strong>: Knife techniques, Sunset over the hills</li></ul>','One live session, 3 hours','<p>2 sessions: Workshop</p>',NULL,'12+ years','1','2','1','1','3',NULL,'{\"certificate\": \"no\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Knife techniques\", \"Sunset over the hills\"]','<p>You finish with a portfolio piece: sunset over the hills.</p>','[\"Finished artworks\", \"Study notes\"]','<p>None, everything is explained from scratch.</p>','2026-11-07','instructor_based',NULL,0,1,'2026-08-07 14:22:13','2026-10-19 14:22:13'),(124,NULL,12,3,'3',2,2,NULL,'Glass & Reflections Drawing','glass-and-reflections-drawing','Learn to draw transparent glass convincingly.','Learn to draw transparent glass convincingly.','<p>Learn to draw transparent glass convincingly. This workshop is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','1 modules, 2 lessons and a final project.','<ul><li><strong>Workshop</strong>: Transparency, A glass bottle</li></ul>','One live session, 3 hours','<p>3 hours: Workshop</p>',NULL,'13+ years','1','1','1','1','3',NULL,'{\"certificate\": \"no\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Transparency\", \"A glass bottle\"]','<p>You finish with a portfolio piece: a glass bottle.</p>','[\"Finished artworks\", \"Study notes\"]','<p>Comfortable with basic drawing.</p>','2026-11-13','instructor_based',NULL,0,1,'2026-08-11 14:22:13','2026-10-19 14:22:13'),(125,NULL,2,102,'3,101',2,1,NULL,'Kids\' Weekend Art Camp','kids-weekend-art-camp','Two fun mornings of drawing and painting for children.','Two fun mornings of drawing and painting for children.','<p>Two fun mornings of drawing and painting for children. This workshop is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','2 modules, 2 lessons and a final project.','<ul><li><strong>Day 1</strong>: Colourful fruit</li><li><strong>Day 2</strong>: Paint a parrot</li></ul>','One live session, 3 hours','<p>Saturday: Day 1<br>Sunday: Day 2</p>',NULL,'6-12 years','1','2','2','2','3',NULL,'{\"certificate\": \"no\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Colourful fruit\", \"Paint a parrot\"]','<p>You finish with a portfolio piece: paint a parrot.</p>','[\"Finished artworks\", \"Study notes\"]','<p>None, everything is explained from scratch.</p>','2026-11-20','instructor_based',NULL,0,1,'2026-08-15 14:22:13','2026-10-19 14:22:13'),(126,NULL,15,6,'4',2,1,NULL,'Digital Lettering Basics','digital-lettering-basics','Brush lettering on the iPad for beginners.','Brush lettering on the iPad for beginners.','<p>Brush lettering on the iPad for beginners. This workshop is taught live in small groups, with personal feedback on every piece you share.</p><h3>What makes it different</h3><ul><li>Live demonstrations you can pause and ask about</li><li>Clear weekly goals</li><li>A friendly community of learners</li></ul>','1 modules, 2 lessons and a final project.','<ul><li><strong>Workshop</strong>: Pressure and strokes, A lettered quote</li></ul>','One live session, 3 hours','<p>3 hours: Workshop</p>',NULL,'13+ years','1','1','1','1','3',NULL,'{\"certificate\": \"no\", \"study_material_access\": \"yes\", \"medium\": \"yes\", \"age\": \"yes\", \"skills\": \"yes\"}','[\"Live classes\", \"Personal feedback\", \"Lifetime access to notes\"]','[\"Beginners who want structure\", \"Hobbyists returning to art\"]','[\"People looking for a recorded-only course\"]','[\"Not knowing where to start\", \"Paintings that look flat\", \"No feedback on your work\"]','[\"Pressure and strokes\", \"A lettered quote\"]','<p>You finish with a portfolio piece: a lettered quote.</p>','[\"Finished artworks\", \"Study notes\"]','<p>None, everything is explained from scratch.</p>','2026-10-04','instructor_based',NULL,0,1,'2026-08-19 14:22:13','2026-10-19 14:22:13');
/*!40000 ALTER TABLE `courses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Temporary view structure for view `courses_workshops`
--

DROP TABLE IF EXISTS `courses_workshops`;
/*!50001 DROP VIEW IF EXISTS `courses_workshops`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `courses_workshops` AS SELECT 
 1 AS `id`,
 1 AS `title`,
 1 AS `description`,
 1 AS `thumbnail_url`,
 1 AS `type`,
 1 AS `price`,
 1 AS `schedule_date`*/;
SET character_set_client = @saved_cs_client;

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
  `progress_percentage` int NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'Active',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `enrollments_user_course_unique` (`user_id`,`course_id`),
  KEY `enrollments_user_id_index` (`user_id`),
  KEY `enrollments_course_id_index` (`course_id`),
  KEY `enrollments_status_index` (`status`),
  CONSTRAINT `enrollments_course_id_foreign` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `enrollments_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `enrollments_chk_1` CHECK ((`progress_percentage` between 0 and 100)),
  CONSTRAINT `enrollments_status_check` CHECK ((`status` in (_utf8mb4'Active',_utf8mb4'Completed',_utf8mb4'Refunded')))
) ENGINE=InnoDB AUTO_INCREMENT=1032 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `enrollments`
--

LOCK TABLES `enrollments` WRITE;
/*!40000 ALTER TABLE `enrollments` DISABLE KEYS */;
INSERT INTO `enrollments` VALUES (1,1,1,100,'Completed','2026-10-24 13:27:29','2026-10-24 13:27:29'),(1001,101,101,43,'Active','2026-05-27 14:22:13','2026-05-30 14:22:13'),(1002,101,112,100,'Completed','2026-06-26 14:22:13','2026-06-29 14:22:13'),(1003,101,121,0,'Active','2026-10-04 14:22:13','2026-10-07 14:22:13'),(1005,102,103,71,'Active','2026-06-06 14:22:13','2026-06-09 14:22:13'),(1006,102,104,40,'Active','2026-07-21 14:22:13','2026-07-24 14:22:13'),(1007,102,122,50,'Active','2026-10-09 14:22:13','2026-10-12 14:22:13'),(1008,102,112,100,'Completed','2026-07-06 14:22:13','2026-07-09 14:22:13'),(1009,103,108,33,'Active','2026-06-16 14:22:13','2026-06-19 14:22:13'),(1010,103,107,67,'Active','2026-08-25 14:22:13','2026-08-28 14:22:13'),(1011,103,123,0,'Active','2026-10-12 14:22:13','2026-10-15 14:22:13'),(1013,104,106,100,'Completed','2026-07-16 14:22:13','2026-07-19 14:22:13'),(1014,104,110,17,'Active','2026-09-09 14:22:13','2026-09-12 14:22:13'),(1015,104,125,0,'Active','2026-10-14 14:22:13','2026-10-17 14:22:13'),(1017,105,105,100,'Completed','2026-07-26 14:22:13','2026-07-29 14:22:13'),(1018,105,109,50,'Active','2026-08-15 14:22:13','2026-08-18 14:22:13'),(1019,105,111,100,'Completed','2026-06-11 14:22:13','2026-06-14 14:22:13'),(1021,106,110,50,'Active','2026-08-05 14:22:13','2026-08-08 14:22:13'),(1022,106,111,100,'Completed','2026-06-21 14:22:13','2026-06-24 14:22:13'),(1023,106,126,100,'Completed','2026-09-29 14:22:13','2026-10-02 14:22:13'),(1026,101,102,17,'Active','2026-09-14 14:22:13','2026-09-17 14:22:13'),(1027,102,109,0,'Active','2026-09-19 14:22:13','2026-09-22 14:22:13'),(1028,103,101,29,'Active','2026-08-30 14:22:13','2026-09-02 14:22:13'),(1029,104,107,0,'Active','2026-09-26 14:22:13','2026-09-29 14:22:13'),(1030,105,121,0,'Active','2026-10-06 14:22:13','2026-10-09 14:22:13'),(1031,1,110,0,'Active','2026-10-24 14:40:04','2026-10-24 14:40:04');
/*!40000 ALTER TABLE `enrollments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `faqs`
--

DROP TABLE IF EXISTS `faqs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `faqs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(100) DEFAULT NULL,
  `question` varchar(500) NOT NULL,
  `answer` longtext,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=106 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `faqs`
--

LOCK TABLES `faqs` WRITE;
/*!40000 ALTER TABLE `faqs` DISABLE KEYS */;
INSERT INTO `faqs` VALUES (1,'Classes','How do the live classes work?','<p>Each course runs as a series of live online sessions with your instructor. After you enrol, the schedule, lessons and tasks appear under <strong>My studio &gt; My courses</strong>.</p>',1,1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,'Classes','What do I need to start?','<p>Every course page lists the materials you need. A phone, tablet or computer with a good internet connection is enough to join the sessions.</p>',2,1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(3,'Payments','Which payment methods can I use?','<p>In India you can pay with UPI, cards and NetBanking. From other countries you can pay with PayPal.</p>',3,1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(4,'Certificates','Will I get a certificate?','<p>Yes. When you complete the lessons and your final work is approved, a verifiable certificate is added to <strong>My studio &gt; Certificates &amp; reports</strong>.</p>',4,1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(5,'Marketplace','Can I sell my artwork on ArtistikCity?','<p>Approved student work can be listed as a 1-of-1 original in the marketplace from your portfolio.</p>',5,1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(101,'Classes','What if I miss a live class?','<p>Every session is recorded and appears in your classroom within a day, so you can catch up and still send your work for feedback.</p>',10,1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(102,'Classes','How big are the groups?','<p>Courses have up to 20 students and workshops up to 12, so the instructor can look at everyone\'s work.</p>',11,1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(103,'Payments','Can I get a refund?','<p>Write to us before the second session and we will refund you in full. Contact us for anything after that.</p>',12,1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(104,'Kids','Are there classes for children?','<p>Yes. Look for the courses marked 8-12 years and our weekend art camp for 6-12 year olds.</p>',13,1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(105,'Marketplace','How are originals shipped?','<p>Originals are packed flat or rolled, insured and shipped with tracking within five working days.</p>',14,1,'2026-10-24 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `faqs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `free_course_videos`
--

DROP TABLE IF EXISTS `free_course_videos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `free_course_videos` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `free_course_id` bigint NOT NULL,
  `video_title` varchar(255) DEFAULT NULL,
  `video_url` varchar(1000) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `free_course_videos`
--

LOCK TABLES `free_course_videos` WRITE;
/*!40000 ALTER TABLE `free_course_videos` DISABLE KEYS */;
INSERT INTO `free_course_videos` VALUES (1,1,'The Colour Wheel','https://www.youtube.com/embed/Qj1FK8n7WgY',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(101,101,'Sketchbook Warm-ups','https://www.youtube.com/embed/Qj1FK8n7WgY',1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(102,102,'Mixing Greens in Watercolour','https://www.youtube.com/embed/Qj1FK8n7WgY',1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(103,103,'Holding the Charcoal','https://www.youtube.com/embed/Qj1FK8n7WgY',1,'2026-10-24 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `free_course_videos` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `free_courses`
--

DROP TABLE IF EXISTS `free_courses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `free_courses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint NOT NULL,
  `title` varchar(255) NOT NULL,
  `slug` varchar(255) NOT NULL,
  `description` longtext,
  `status` tinyint DEFAULT NULL,
  `photo` varchar(500) DEFAULT NULL,
  `video_url` varchar(1000) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `free_courses`
--

LOCK TABLES `free_courses` WRITE;
/*!40000 ALTER TABLE `free_courses` DISABLE KEYS */;
INSERT INTO `free_courses` VALUES (1,2,'Introduction to Colour Theory','introduction-to-colour-theory','Primary, secondary and complementary colours explained with simple exercises.',1,'free.jpg',NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(101,12,'Sketchbook Warm-ups','sketchbook-warm-ups','<p>Five-minute drawing warm-ups to loosen your hand before every session.</p>',1,'free.jpg',NULL,'2026-09-04 14:22:13','2026-10-24 14:22:13'),(102,11,'Mixing Greens in Watercolour','mixing-greens-in-watercolour','<p>Mix natural greens from just two blues and two yellows.</p>',1,'free.jpg',NULL,'2026-09-04 14:22:13','2026-10-24 14:22:13'),(103,12,'Holding the Charcoal','holding-the-charcoal','<p>Grips, pressure and the three marks every charcoal artist uses.</p>',1,'free.jpg',NULL,'2026-09-04 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `free_courses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `genres`
--

DROP TABLE IF EXISTS `genres`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `genres` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `genre_slug` varchar(100) NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `genres`
--

LOCK TABLES `genres` WRITE;
/*!40000 ALTER TABLE `genres` DISABLE KEYS */;
INSERT INTO `genres` VALUES (1,'Landscape','landscape',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,'Portrait','portrait',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(3,'Still Life','still-life',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(4,'Abstract','abstract',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(101,'Wildlife','wildlife',1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(102,'Botanical','botanical',1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(103,'Urban Sketching','urban-sketching',1,'2026-10-24 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `genres` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `home_artworks`
--

DROP TABLE IF EXISTS `home_artworks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `home_artworks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint NOT NULL,
  `photo_name` varchar(500) DEFAULT NULL,
  `comments` longtext,
  `status` tinyint DEFAULT '0',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=105 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `home_artworks`
--

LOCK TABLES `home_artworks` WRITE;
/*!40000 ALTER TABLE `home_artworks` DISABLE KEYS */;
INSERT INTO `home_artworks` VALUES (1,1,'artwork.jpg','Student landscape',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,1,'artwork.jpg','Student portrait',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(3,1,'artwork.jpg','Student still life',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(4,1,'artwork.jpg','Student florals',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(101,1,'artwork.jpg','Student portrait, charcoal',1,'2026-09-24 14:22:13','2026-10-24 14:22:13'),(102,1,'artwork.jpg','Acrylic landscape by a beginner',1,'2026-09-25 14:22:13','2026-10-24 14:22:13'),(103,1,'artwork.jpg','Kids class: parrot in pencil',1,'2026-09-26 14:22:13','2026-10-24 14:22:13'),(104,1,'artwork.jpg','Oil pastel still life',1,'2026-09-27 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `home_artworks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lesson_completions`
--

DROP TABLE IF EXISTS `lesson_completions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lesson_completions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enrollment_id` bigint NOT NULL,
  `lesson_id` bigint NOT NULL,
  `completed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `lesson_completions_unique` (`enrollment_id`,`lesson_id`),
  KEY `lesson_completions_enrollment_id_index` (`enrollment_id`),
  KEY `lesson_completions_lesson_id_index` (`lesson_id`),
  CONSTRAINT `lesson_completions_enrollment_id_foreign` FOREIGN KEY (`enrollment_id`) REFERENCES `enrollments` (`id`),
  CONSTRAINT `lesson_completions_lesson_id_foreign` FOREIGN KEY (`lesson_id`) REFERENCES `course_module_lessons` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=102802 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lesson_completions`
--

LOCK TABLES `lesson_completions` WRITE;
/*!40000 ALTER TABLE `lesson_completions` DISABLE KEYS */;
INSERT INTO `lesson_completions` VALUES (1,1,1,'2026-10-24 13:27:29'),(2,1,2,'2026-10-24 13:27:29'),(3,1,3,'2026-10-24 13:27:29'),(100100,1001,10101,'2026-05-29 14:22:13'),(100101,1001,10102,'2026-06-01 14:22:13'),(100102,1001,10103,'2026-06-04 14:22:13'),(100200,1002,11201,'2026-06-28 14:22:13'),(100201,1002,11202,'2026-07-01 14:22:13'),(100202,1002,11203,'2026-07-04 14:22:13'),(100203,1002,11204,'2026-07-07 14:22:13'),(100500,1005,10301,'2026-06-08 14:22:13'),(100501,1005,10302,'2026-06-11 14:22:13'),(100502,1005,10303,'2026-06-14 14:22:13'),(100503,1005,10304,'2026-06-17 14:22:13'),(100504,1005,10305,'2026-06-20 14:22:13'),(100600,1006,10401,'2026-07-23 14:22:13'),(100601,1006,10402,'2026-07-26 14:22:13'),(100700,1007,12201,'2026-10-11 14:22:13'),(100800,1008,11201,'2026-07-08 14:22:13'),(100801,1008,11202,'2026-07-11 14:22:13'),(100802,1008,11203,'2026-07-14 14:22:13'),(100803,1008,11204,'2026-07-17 14:22:13'),(100900,1009,10801,'2026-06-18 14:22:13'),(100901,1009,10802,'2026-06-21 14:22:13'),(101000,1010,10701,'2026-08-27 14:22:13'),(101001,1010,10702,'2026-08-30 14:22:13'),(101002,1010,10703,'2026-09-02 14:22:13'),(101003,1010,10704,'2026-09-05 14:22:13'),(101300,1013,10601,'2026-07-18 14:22:13'),(101301,1013,10602,'2026-07-21 14:22:13'),(101302,1013,10603,'2026-07-24 14:22:13'),(101303,1013,10604,'2026-07-27 14:22:13'),(101304,1013,10605,'2026-07-30 14:22:13'),(101400,1014,11001,'2026-09-11 14:22:13'),(101700,1017,10501,'2026-07-28 14:22:13'),(101701,1017,10502,'2026-07-31 14:22:13'),(101702,1017,10503,'2026-08-03 14:22:13'),(101703,1017,10504,'2026-08-06 14:22:13'),(101704,1017,10505,'2026-08-09 14:22:13'),(101800,1018,10901,'2026-08-17 14:22:13'),(101801,1018,10902,'2026-08-20 14:22:13'),(101900,1019,11101,'2026-06-13 14:22:13'),(101901,1019,11102,'2026-06-16 14:22:13'),(101902,1019,11103,'2026-06-19 14:22:13'),(101903,1019,11104,'2026-06-22 14:22:13'),(102100,1021,11001,'2026-08-07 14:22:13'),(102101,1021,11002,'2026-08-10 14:22:13'),(102102,1021,11003,'2026-08-13 14:22:13'),(102200,1022,11101,'2026-06-23 14:22:13'),(102201,1022,11102,'2026-06-26 14:22:13'),(102202,1022,11103,'2026-06-29 14:22:13'),(102203,1022,11104,'2026-07-02 14:22:13'),(102300,1023,12601,'2026-10-01 14:22:13'),(102301,1023,12602,'2026-10-04 14:22:13'),(102600,1026,10201,'2026-09-16 14:22:13'),(102800,1028,10101,'2026-09-01 14:22:13'),(102801,1028,10102,'2026-09-04 14:22:13');
/*!40000 ALTER TABLE `lesson_completions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `linked_accounts`
--

DROP TABLE IF EXISTS `linked_accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `linked_accounts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `provider_name` varchar(20) NOT NULL,
  `provider_user_id` varchar(191) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `email_verified` tinyint(1) NOT NULL DEFAULT '0',
  `display_name` varchar(255) DEFAULT NULL,
  `first_name` varchar(100) DEFAULT NULL,
  `last_name` varchar(100) DEFAULT NULL,
  `avatar_url` varchar(1000) DEFAULT NULL,
  `profile_data` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_login_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `linked_accounts_provider_unique` (`provider_name`,`provider_user_id`),
  UNIQUE KEY `linked_accounts_user_provider_unique` (`user_id`,`provider_name`),
  CONSTRAINT `linked_accounts_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `linked_accounts_chk_1` CHECK ((`provider_name` in (_utf8mb4'google',_utf8mb4'facebook')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `linked_accounts`
--

LOCK TABLES `linked_accounts` WRITE;
/*!40000 ALTER TABLE `linked_accounts` DISABLE KEYS */;
/*!40000 ALTER TABLE `linked_accounts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `marketplace_orders`
--

DROP TABLE IF EXISTS `marketplace_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `marketplace_orders` (
  `order_id` varchar(50) NOT NULL,
  `painting_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `holder_key` varchar(255) NOT NULL,
  `buyer_name` varchar(255) NOT NULL,
  `buyer_email` varchar(255) NOT NULL,
  `buyer_phone` varchar(50) DEFAULT NULL,
  `address_line1` varchar(255) NOT NULL,
  `address_line2` varchar(255) DEFAULT NULL,
  `city` varchar(100) NOT NULL,
  `state` varchar(100) DEFAULT NULL,
  `postal_code` varchar(20) NOT NULL,
  `country` varchar(100) NOT NULL,
  `gateway` varchar(20) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `price_inr` decimal(12,2) NOT NULL,
  `provider_ref` varchar(255) DEFAULT NULL,
  `transaction_id` varchar(255) DEFAULT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'PAYMENT_PENDING',
  `courier` varchar(100) DEFAULT NULL,
  `tracking_number` varchar(100) DEFAULT NULL,
  `staff_notes` longtext,
  `paid_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`order_id`),
  KEY `marketplace_orders_user_id_foreign` (`user_id`),
  KEY `marketplace_orders_painting_id_index` (`painting_id`),
  KEY `marketplace_orders_status_index` (`status`),
  KEY `marketplace_orders_provider_ref_index` (`provider_ref`),
  CONSTRAINT `marketplace_orders_painting_id_foreign` FOREIGN KEY (`painting_id`) REFERENCES `paintings` (`id`),
  CONSTRAINT `marketplace_orders_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `marketplace_orders_chk_1` CHECK ((`gateway` in (_utf8mb4'RAZORPAY',_utf8mb4'PAYPAL',_utf8mb4'TEST'))),
  CONSTRAINT `marketplace_orders_chk_2` CHECK ((`status` in (_utf8mb4'PAYMENT_PENDING',_utf8mb4'PAID',_utf8mb4'PACKED',_utf8mb4'SHIPPED',_utf8mb4'DELIVERED',_utf8mb4'CANCELLED',_utf8mb4'EXPIRED',_utf8mb4'REFUND_REQUIRED',_utf8mb4'REFUNDED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `marketplace_orders`
--

LOCK TABLES `marketplace_orders` WRITE;
/*!40000 ALTER TABLE `marketplace_orders` DISABLE KEYS */;
INSERT INTO `marketplace_orders` VALUES ('AC-M-DEMO01',4,NULL,'S:demo','Meera Iyer','meera@example.com','9000000001','12 Lavelle Road',NULL,'Bengaluru','Karnataka','560001','India','RAZORPAY','INR',19200.00,19200.00,'order_demo01','pay_demo01','SHIPPED','Blue Dart','BD123456789IN',NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29','2026-10-24 13:27:29'),('AC-M-DEMO02',10,NULL,'S:demo','James Carter','james@example.com',NULL,'221 Baker Street',NULL,'London',NULL,'NW1 6XE','United Kingdom','PAYPAL','USD',142.86,12000.00,'PAYPAL-DEMO02','CAPTURE-DEMO02','PAID',NULL,NULL,NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29','2026-10-24 13:27:29'),('AC-M-DEMO101',106,103,'U:103','Arjun Menon','arjun@example.com','9811000103','24 Marine Drive',NULL,'Kochi','Kerala','682011','India','RAZORPAY','INR',8500.00,8500.00,'order_DEMOM101','pay_DEMOM101','DELIVERED','DTDC','DT987654321IN',NULL,'2026-10-14 14:22:13','2026-10-14 14:22:13','2026-10-20 14:22:13');
/*!40000 ALTER TABLE `marketplace_orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mediums`
--

DROP TABLE IF EXISTS `mediums`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mediums` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `medium_slug` varchar(100) NOT NULL,
  `photo` varchar(500) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=103 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mediums`
--

LOCK TABLES `mediums` WRITE;
/*!40000 ALTER TABLE `mediums` DISABLE KEYS */;
INSERT INTO `mediums` VALUES (1,'Watercolour','watercolour','medium.jpg',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,'Acrylic','acrylic','medium.jpg',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(3,'Pencil Sketch','pencil-sketch','medium.jpg',0,'2026-10-24 13:27:29','2026-10-25 15:28:26'),(4,'Oil Painting','oil-painting','medium.jpg',0,'2026-10-24 13:27:29','2026-10-25 15:28:21'),(5,'Charcoal','charcoal','medium.jpg',0,'2026-10-24 13:27:29','2026-10-25 15:28:05'),(6,'Digital Art','digital-art','medium.jpg',0,'2026-10-24 13:27:29','2026-10-25 15:28:10'),(101,'Ink & Pen','ink-and-pen','medium.jpg',1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(102,'Mixed Media','mixed-media','medium.jpg',0,'2026-10-24 14:22:13','2026-10-25 15:28:14');
/*!40000 ALTER TABLE `mediums` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `newsletter_campaigns`
--

DROP TABLE IF EXISTS `newsletter_campaigns`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `newsletter_campaigns` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `subject` varchar(255) NOT NULL,
  `preheader` varchar(255) DEFAULT NULL,
  `body` longtext,
  `status` varchar(20) NOT NULL DEFAULT 'draft',
  `recipients` int NOT NULL DEFAULT '0',
  `sent_count` int NOT NULL DEFAULT '0',
  `failed_count` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `sent_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=103 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `newsletter_campaigns`
--

LOCK TABLES `newsletter_campaigns` WRITE;
/*!40000 ALTER TABLE `newsletter_campaigns` DISABLE KEYS */;
INSERT INTO `newsletter_campaigns` VALUES (101,'October at the studio: new workshops and student stars','Three new workshops and Neha\'s portrait story','<h2>Hello from the studio!</h2><p>This month we are launching three weekend workshops: loose florals, pet portraits in charcoal and a palette knife sunset.</p><p>We are also celebrating Neha, whose charcoal portrait is now in the marketplace.</p><p><a href=\"/courses?type=workshop\">See the workshops</a></p>','sent',24,24,0,1,'2026-10-15 14:22:13','2026-10-13 14:22:13','2026-10-15 14:22:13'),(102,'November preview: oil painting and digital art','Early access for subscribers','<p>Draft: announce the November batch of Oil Painting Foundations and Digital Illustration with Procreate.</p>','draft',0,0,0,1,NULL,'2026-10-23 14:22:13','2026-10-23 14:22:13');
/*!40000 ALTER TABLE `newsletter_campaigns` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `newsletters`
--

DROP TABLE IF EXISTS `newsletters`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `newsletters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `source` varchar(50) DEFAULT NULL,
  `unsubscribed_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `newsletters_email_index` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=137 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `newsletters`
--

LOCK TABLES `newsletters` WRITE;
/*!40000 ALTER TABLE `newsletters` DISABLE KEYS */;
INSERT INTO `newsletters` VALUES (101,'asha@example.com','admin',NULL,'2026-05-17 14:22:13','2026-05-17 14:22:13'),(102,'kiran@example.com','footer',NULL,'2026-05-24 14:22:13','2026-05-24 14:22:13'),(103,'leela@example.com','footer',NULL,'2026-05-31 14:22:13','2026-05-31 14:22:13'),(104,'manoj@example.com','footer',NULL,'2026-06-07 14:22:13','2026-06-07 14:22:13'),(105,'nisha@example.com','footer','2026-10-19 14:22:13','2026-06-14 14:22:13','2026-06-14 14:22:13'),(106,'omar@example.com','footer',NULL,'2026-06-21 14:22:13','2026-06-21 14:22:13'),(107,'priya@example.com','footer',NULL,'2026-06-28 14:22:13','2026-06-28 14:22:13'),(108,'qasim@example.com','admin',NULL,'2026-07-05 14:22:13','2026-07-05 14:22:13'),(109,'ritu@example.com','footer',NULL,'2026-07-12 14:22:13','2026-07-12 14:22:13'),(110,'sameer@example.com','footer',NULL,'2026-07-19 14:22:13','2026-07-19 14:22:13'),(111,'tara@example.com','footer',NULL,'2026-07-26 14:22:13','2026-07-26 14:22:13'),(112,'uday@example.com','footer',NULL,'2026-08-02 14:22:13','2026-08-02 14:22:13'),(113,'vani@example.com','footer',NULL,'2026-08-09 14:22:13','2026-08-09 14:22:13'),(114,'wasim@example.com','footer','2026-10-19 14:22:13','2026-08-16 14:22:13','2026-08-16 14:22:13'),(115,'yash@example.com','admin',NULL,'2026-08-23 14:22:13','2026-08-23 14:22:13'),(116,'zoya@example.com','footer',NULL,'2026-08-30 14:22:13','2026-08-30 14:22:13'),(117,'aditi@example.com','footer',NULL,'2026-09-06 14:22:13','2026-09-06 14:22:13'),(118,'bhavesh@example.com','footer',NULL,'2026-09-13 14:22:13','2026-09-13 14:22:13'),(119,'chitra@example.com','footer',NULL,'2026-09-20 14:22:13','2026-09-20 14:22:13'),(120,'dev@example.com','footer',NULL,'2026-09-27 14:22:13','2026-09-27 14:22:13'),(131,'ishaan@example.com','footer',NULL,'2026-06-06 14:22:13','2026-10-24 14:22:13'),(132,'neha@example.com','footer',NULL,'2026-06-15 14:22:13','2026-10-24 14:22:13'),(133,'arjun@example.com','footer',NULL,'2026-06-24 14:22:13','2026-10-24 14:22:13'),(134,'sophia@example.com','footer',NULL,'2026-07-03 14:22:13','2026-10-24 14:22:13'),(135,'rahul@example.com','footer',NULL,'2026-07-12 14:22:13','2026-10-24 14:22:13'),(136,'emily@example.com','footer',NULL,'2026-07-21 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `newsletters` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_number` varchar(100) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `session_id` varchar(255) NOT NULL,
  `billing_name` varchar(255) NOT NULL,
  `billing_email` varchar(255) DEFAULT NULL,
  `billing_address` varchar(500) DEFAULT NULL,
  `billing_city` varchar(255) DEFAULT NULL,
  `billing_state` varchar(255) DEFAULT NULL,
  `billing_zipcode` varchar(50) DEFAULT NULL,
  `billing_country` varchar(255) DEFAULT NULL,
  `price_type` varchar(10) DEFAULT NULL,
  `price` varchar(50) DEFAULT NULL,
  `payment_option` varchar(50) DEFAULT NULL,
  `payment_id` varchar(255) DEFAULT NULL,
  `gateway_ref` varchar(255) DEFAULT NULL,
  `payment_status` tinyint NOT NULL DEFAULT '0',
  `payment_details` longtext,
  `issue_certificate` tinyint NOT NULL DEFAULT '0',
  `certificate_name` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `orders_gateway_ref_index` (`gateway_ref`),
  KEY `orders_user_course_index` (`user_id`,`course_id`),
  KEY `orders_created_index` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=1032 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
INSERT INTO `orders` VALUES (1,'ORD00001',1,1,'seed','Aarav Sharma','student@artistikcity.com','12 MG Road','Mumbai','Maharashtra','400001','India','inr','4999','RazorPay','pay_demo_001',NULL,1,NULL,0,NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(1001,'ACD1001',101,101,'demo','Ishaan Verma','ishaan@example.com','10 Main Road','Jaipur','Rajasthan','302001','India','inr','5999','RazorPay','pay_DEMO1001','order_DEMO1001',1,'{\"note\": \"demo data\"}',0,NULL,'2026-05-27 14:22:13','2026-05-28 14:22:13'),(1002,'ACD1002',101,112,'demo','Ishaan Verma','ishaan@example.com','10 Main Road','Jaipur','Rajasthan','302001','India','inr','1999','RazorPay','pay_DEMO1002','order_DEMO1002',1,'{\"note\": \"demo data\"}',0,NULL,'2026-06-26 13:22:13','2026-06-27 14:22:13'),(1003,'ACD1003',101,121,'demo','Ishaan Verma','ishaan@example.com','10 Main Road','Jaipur','Rajasthan','302001','India','inr','999','RazorPay','pay_DEMO1003','order_DEMO1003',1,'{\"note\": \"demo data\"}',0,NULL,'2026-10-04 12:22:13','2026-10-05 14:22:13'),(1004,'ACD1004',101,107,'demo','Ishaan Verma','ishaan@example.com','10 Main Road','Jaipur','Rajasthan','302001','India','inr','4499',NULL,NULL,'order_DEMO1004',0,NULL,0,NULL,'2026-10-22 11:22:13','2026-10-23 14:22:13'),(1005,'ACD1005',102,103,'demo','Neha Kulkarni','neha@example.com','17 Main Road','Pune','Maharashtra','411004','India','inr','8999','RazorPay','pay_DEMO1005','order_DEMO1005',1,'{\"note\": \"demo data\"}',0,NULL,'2026-06-06 10:22:13','2026-06-07 14:22:13'),(1006,'ACD1006',102,104,'demo','Neha Kulkarni','neha@example.com','17 Main Road','Pune','Maharashtra','411004','India','inr','5499','RazorPay','pay_DEMO1006','order_DEMO1006',1,'{\"note\": \"demo data\"}',0,NULL,'2026-07-21 09:22:13','2026-07-22 14:22:13'),(1007,'ACD1007',102,122,'demo','Neha Kulkarni','neha@example.com','17 Main Road','Pune','Maharashtra','411004','India','inr','1299','RazorPay','pay_DEMO1007','order_DEMO1007',1,'{\"note\": \"demo data\"}',0,NULL,'2026-10-09 08:22:13','2026-10-10 14:22:13'),(1008,'ACD1008',102,112,'demo','Neha Kulkarni','neha@example.com','17 Main Road','Pune','Maharashtra','411004','India','inr','1999','RazorPay','pay_DEMO1008','order_DEMO1008',1,'{\"note\": \"demo data\"}',0,NULL,'2026-07-06 14:22:13','2026-07-07 14:22:13'),(1009,'ACD1009',103,108,'demo','Arjun Menon','arjun@example.com','24 Main Road','Kochi','Kerala','682011','India','inr','7999','RazorPay','pay_DEMO1009','order_DEMO1009',1,'{\"note\": \"demo data\"}',0,NULL,'2026-06-16 13:22:13','2026-06-17 14:22:13'),(1010,'ACD1010',103,107,'demo','Arjun Menon','arjun@example.com','24 Main Road','Kochi','Kerala','682011','India','inr','4499','RazorPay','pay_DEMO1010','order_DEMO1010',1,'{\"note\": \"demo data\"}',0,NULL,'2026-08-25 12:22:13','2026-08-26 14:22:13'),(1011,'ACD1011',103,123,'demo','Arjun Menon','arjun@example.com','24 Main Road','Kochi','Kerala','682011','India','inr','1199','RazorPay','pay_DEMO1011','order_DEMO1011',1,'{\"note\": \"demo data\"}',0,NULL,'2026-10-12 11:22:13','2026-10-13 14:22:13'),(1012,'ACD1012',103,102,'demo','Arjun Menon','arjun@example.com','24 Main Road','Kochi','Kerala','682011','India','inr','6499','RazorPay',NULL,'order_DEMO1012',2,NULL,0,NULL,'2026-10-16 10:22:13','2026-10-17 14:22:13'),(1013,'ACD1013',104,106,'demo','Sophia Fernandes','sophia@example.com','31 Main Road','Panaji','Goa','403001','India','inr','4999','RazorPay','pay_DEMO1013','order_DEMO1013',1,'{\"note\": \"demo data\"}',0,NULL,'2026-07-16 09:22:13','2026-07-17 14:22:13'),(1014,'ACD1014',104,110,'demo','Sophia Fernandes','sophia@example.com','31 Main Road','Panaji','Goa','403001','India','inr','6999','RazorPay','pay_DEMO1014','order_DEMO1014',1,'{\"note\": \"demo data\"}',0,NULL,'2026-09-09 08:22:13','2026-09-10 14:22:13'),(1015,'ACD1015',104,125,'demo','Sophia Fernandes','sophia@example.com','31 Main Road','Panaji','Goa','403001','India','inr','1499','RazorPay','pay_DEMO1015','order_DEMO1015',1,'{\"note\": \"demo data\"}',0,NULL,'2026-10-14 14:22:13','2026-10-15 14:22:13'),(1016,'ACD1016',104,113,'demo','Sophia Fernandes','sophia@example.com','31 Main Road','Panaji','Goa','403001','India','inr','2999',NULL,NULL,'order_DEMO1016',0,NULL,0,NULL,'2026-10-23 13:22:13','2026-10-24 14:22:13'),(1017,'ACD1017',105,105,'demo','Rahul Gupta','rahul@example.com','38 Main Road','Lucknow','Uttar Pradesh','226001','India','inr','2499','RazorPay','pay_DEMO1017','order_DEMO1017',1,'{\"note\": \"demo data\"}',0,NULL,'2026-07-26 12:22:13','2026-07-27 14:22:13'),(1018,'ACD1018',105,109,'demo','Rahul Gupta','rahul@example.com','38 Main Road','Lucknow','Uttar Pradesh','226001','India','inr','3999','RazorPay','pay_DEMO1018','order_DEMO1018',1,'{\"note\": \"demo data\"}',0,NULL,'2026-08-15 11:22:13','2026-08-16 14:22:13'),(1019,'ACD1019',105,111,'demo','Rahul Gupta','rahul@example.com','38 Main Road','Lucknow','Uttar Pradesh','226001','India','inr','3499','RazorPay','pay_DEMO1019','order_DEMO1019',1,'{\"note\": \"demo data\"}',0,NULL,'2026-06-11 10:22:13','2026-06-12 14:22:13'),(1020,'ACD1020',105,124,'demo','Rahul Gupta','rahul@example.com','38 Main Road','Lucknow','Uttar Pradesh','226001','India','inr','899','RazorPay','pay_DEMO1020','order_DEMO1020',3,'{\"note\": \"demo data\"}',0,NULL,'2026-09-24 09:22:13','2026-09-25 14:22:13'),(1021,'ACD1021',106,110,'demo','Emily Clarke','emily@example.com','45 Main Road','London',NULL,'SW1A 1AA','United Kingdom','usd','89','PayPal','pay_DEMO1021','order_DEMO1021',1,'{\"note\": \"demo data\"}',0,NULL,'2026-08-05 08:22:13','2026-08-06 14:22:13'),(1022,'ACD1022',106,111,'demo','Emily Clarke','emily@example.com','45 Main Road','London',NULL,'SW1A 1AA','United Kingdom','usd','45','PayPal','pay_DEMO1022','order_DEMO1022',1,'{\"note\": \"demo data\"}',0,NULL,'2026-06-21 14:22:13','2026-06-22 14:22:13'),(1023,'ACD1023',106,126,'demo','Emily Clarke','emily@example.com','45 Main Road','London',NULL,'SW1A 1AA','United Kingdom','usd','15','PayPal','pay_DEMO1023','order_DEMO1023',1,'{\"note\": \"demo data\"}',0,NULL,'2026-09-29 13:22:13','2026-09-30 14:22:13'),(1024,'ACD1024',106,101,'demo','Emily Clarke','emily@example.com','45 Main Road','London',NULL,'SW1A 1AA','United Kingdom','usd','79',NULL,NULL,'order_DEMO1024',0,NULL,0,NULL,'2026-10-21 12:22:13','2026-10-22 14:22:13'),(1025,'ACD1025',106,103,'demo','Emily Clarke','emily@example.com','45 Main Road','London',NULL,'SW1A 1AA','United Kingdom','usd','119','PayPal',NULL,'order_DEMO1025',2,NULL,0,NULL,'2026-10-18 11:22:13','2026-10-19 14:22:13'),(1026,'ACD1026',101,102,'demo','Ishaan Verma','ishaan@example.com','10 Main Road','Jaipur','Rajasthan','302001','India','inr','6499','RazorPay','pay_DEMO1026','order_DEMO1026',1,'{\"note\": \"demo data\"}',0,NULL,'2026-09-14 10:22:13','2026-09-15 14:22:13'),(1027,'ACD1027',102,109,'demo','Neha Kulkarni','neha@example.com','17 Main Road','Pune','Maharashtra','411004','India','inr','3999','RazorPay','pay_DEMO1027','order_DEMO1027',1,'{\"note\": \"demo data\"}',0,NULL,'2026-09-19 09:22:13','2026-09-20 14:22:13'),(1028,'ACD1028',103,101,'demo','Arjun Menon','arjun@example.com','24 Main Road','Kochi','Kerala','682011','India','inr','5999','RazorPay','pay_DEMO1028','order_DEMO1028',1,'{\"note\": \"demo data\"}',0,NULL,'2026-08-30 08:22:13','2026-08-31 14:22:13'),(1029,'ACD1029',104,107,'demo','Sophia Fernandes','sophia@example.com','31 Main Road','Panaji','Goa','403001','India','inr','4499','RazorPay','pay_DEMO1029','order_DEMO1029',1,'{\"note\": \"demo data\"}',0,NULL,'2026-09-26 14:22:13','2026-09-27 14:22:13'),(1030,'ACD1030',105,121,'demo','Rahul Gupta','rahul@example.com','38 Main Road','Lucknow','Uttar Pradesh','226001','India','inr','999','RazorPay','pay_DEMO1030','order_DEMO1030',1,'{\"note\": \"demo data\"}',0,NULL,'2026-10-06 13:22:13','2026-10-07 14:22:13'),(1031,'ORD01031',1,110,'A3FC3AEDF7F431C21AA23322D3706DB0','Aarav Sharma','student@artistikcity.com','12 MG Road','Mumbai','Maharashtra','400001','India','inr','6999.00','RazorPay (test)','test_1031_1792833002209',NULL,1,'{\"test\":true}',0,NULL,'2026-10-24 14:39:59','2026-10-24 14:40:02');
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `painting_images`
--

DROP TABLE IF EXISTS `painting_images`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `painting_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `painting_id` bigint NOT NULL,
  `image_url` varchar(2083) NOT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `alt_text` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `painting_images_painting_id_index` (`painting_id`),
  CONSTRAINT `painting_images_painting_id_foreign` FOREIGN KEY (`painting_id`) REFERENCES `paintings` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=108 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `painting_images`
--

LOCK TABLES `painting_images` WRITE;
/*!40000 ALTER TABLE `painting_images` DISABLE KEYS */;
INSERT INTO `painting_images` VALUES (1,1,'/assets/images/portfolio-2.jpg',1,0,'Scarlet Macaw Study, graphite drawing'),(2,2,'/assets/images/course1.jpg',1,0,'The Old Mill at Dawn, acrylic landscape'),(3,2,'/assets/images/register-banner.jpg',0,1,'The Old Mill at Dawn, detail'),(4,3,'/assets/images/course2.jpg',1,0,'Lines of a Lifetime, charcoal portrait'),(5,4,'/assets/images/course3.jpg',1,0,'Curls in Charcoal, portrait'),(6,5,'/assets/images/login-banner.jpg',1,0,'Flameback Woodpecker, watercolour'),(7,6,'/assets/images/portfolio-5.jpg',1,0,'Copper Kettle Still Life, graphite'),(8,7,'/storage/uploads/artworks/1/artwork.jpg',1,0,'Monsoon Hills, student watercolour'),(9,8,'/assets/images/portfolio-8.jpg',1,0,'Glass Marble, Morning Light'),(10,9,'/assets/images/portfolio-7.jpg',1,0,'Rose in Monochrome'),(11,10,'/assets/images/portfolio-3.jpg',1,0,'Glass Jars on the Sill'),(101,101,'/assets/images/home-category-image-1.jpg',1,0,'Eye Study in Graphite'),(102,102,'/assets/images/course2.jpg',1,0,'Portrait of My Grandfather'),(103,103,'/assets/images/portfolio-3.jpg',1,0,'Kitchen Jars, Ink Study'),(104,104,'/assets/images/home-category-image-3.jpg',1,0,'Orchard Apples'),(105,105,'/assets/images/home-category-image-4.jpg',1,0,'Himalayan Morning'),(106,106,'/assets/images/home-category-image-2.jpg',1,0,'Sleeping Cat'),(107,107,'/assets/images/home-category-image-6.jpg',1,0,'Ember');
/*!40000 ALTER TABLE `painting_images` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `paintings`
--

DROP TABLE IF EXISTS `paintings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `paintings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `slug` varchar(191) NOT NULL,
  `title` varchar(255) NOT NULL,
  `description` longtext NOT NULL,
  `artist_notes` longtext,
  `medium` varchar(100) NOT NULL,
  `surface` varchar(100) NOT NULL,
  `subject` varchar(100) DEFAULT NULL,
  `style_tags` varchar(255) DEFAULT NULL,
  `height_inches` decimal(6,2) NOT NULL,
  `width_inches` decimal(6,2) NOT NULL,
  `depth_inches` decimal(6,2) NOT NULL DEFAULT '0.00',
  `weight_kg` decimal(6,2) DEFAULT NULL,
  `year_created` int NOT NULL,
  `is_framed` tinyint(1) NOT NULL DEFAULT '0',
  `frame_details` varchar(255) DEFAULT NULL,
  `is_signed` tinyint(1) NOT NULL DEFAULT '1',
  `has_certificate` tinyint(1) NOT NULL DEFAULT '1',
  `base_price` decimal(12,2) NOT NULL,
  `discount_percentage` decimal(5,2) NOT NULL DEFAULT '0.00',
  `final_price` decimal(12,2) NOT NULL,
  `currency` varchar(3) NOT NULL DEFAULT 'INR',
  `stock_status` varchar(30) NOT NULL DEFAULT 'DRAFT',
  `version` int NOT NULL DEFAULT '0',
  `source` varchar(20) NOT NULL DEFAULT 'STUDIO',
  `artist_name` varchar(255) NOT NULL,
  `artist_admin_id` bigint DEFAULT NULL,
  `artist_user_id` bigint DEFAULT NULL,
  `submission_id` bigint DEFAULT NULL,
  `is_featured` tinyint(1) NOT NULL DEFAULT '0',
  `shop_formats` varchar(60) DEFAULT NULL,
  `edition_size` int NOT NULL DEFAULT '50',
  `editions_sold` int NOT NULL DEFAULT '0',
  `hires_path` varchar(255) DEFAULT NULL,
  `hires_name` varchar(255) DEFAULT NULL,
  `artist_whatsapp` varchar(30) DEFAULT NULL,
  `artist_instagram` varchar(120) DEFAULT NULL,
  `artist_email` varchar(190) DEFAULT NULL,
  `review_notes` longtext,
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `sold_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `paintings_slug_unique` (`slug`),
  KEY `paintings_reviewed_by_foreign` (`reviewed_by`),
  KEY `IX_Paintings_Status` (`stock_status`),
  KEY `paintings_artist_admin_id_index` (`artist_admin_id`),
  KEY `paintings_artist_user_id_index` (`artist_user_id`),
  KEY `paintings_submission_id_index` (`submission_id`),
  CONSTRAINT `paintings_artist_admin_id_foreign` FOREIGN KEY (`artist_admin_id`) REFERENCES `admins` (`id`),
  CONSTRAINT `paintings_artist_user_id_foreign` FOREIGN KEY (`artist_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `paintings_reviewed_by_foreign` FOREIGN KEY (`reviewed_by`) REFERENCES `admins` (`id`),
  CONSTRAINT `paintings_submission_id_foreign` FOREIGN KEY (`submission_id`) REFERENCES `student_submissions` (`id`),
  CONSTRAINT `paintings_chk_1` CHECK ((`height_inches` > 0)),
  CONSTRAINT `paintings_chk_2` CHECK ((`width_inches` > 0)),
  CONSTRAINT `paintings_chk_3` CHECK ((`base_price` > 0)),
  CONSTRAINT `paintings_chk_4` CHECK (((`discount_percentage` >= 0) and (`discount_percentage` <= 90))),
  CONSTRAINT `paintings_chk_5` CHECK ((`stock_status` in (_utf8mb4'DRAFT',_utf8mb4'PENDING_REVIEW',_utf8mb4'AVAILABLE',_utf8mb4'RESERVED',_utf8mb4'SOLD',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `paintings_chk_6` CHECK ((`source` in (_utf8mb4'STUDIO',_utf8mb4'STUDENT'))),
  CONSTRAINT `paintings_final_price_check` CHECK ((`final_price` = round(((`base_price` * (100 - `discount_percentage`)) / 100),2)))
) ENGINE=InnoDB AUTO_INCREMENT=108 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `paintings`
--

LOCK TABLES `paintings` WRITE;
/*!40000 ALTER TABLE `paintings` DISABLE KEYS */;
INSERT INTO `paintings` VALUES (1,'scarlet-macaw-study','Scarlet Macaw Study','A close, feather-by-feather study of a macaw at rest, built up in soft graphite layers from 2H to 8B.','I sketched this bird at a friend\'s aviary over three mornings. What held me was the way the light slid across each feather, so I let the background stay quiet and spent the time on texture.','Graphite','Archival 300gsm acid-free paper','Wildlife','Realism, Study',16.00,12.00,0.10,0.60,2025,1,'Black oak float frame with museum glass',1,1,18000.00,15.00,15300.00,'INR','AVAILABLE',2,'STUDIO','Vaishali J Goswami',2,NULL,NULL,1,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-24 13:27:29',NULL,'2026-10-24 13:27:29','2026-11-05 12:10:00'),(2,'the-old-mill-at-dawn','The Old Mill at Dawn','A forest mill beside a rushing stream, with moss-covered rocks and early light filtering through the pines.','This painting began as a memory of a trek in the hills. I kept the water loose and fast and slowed down on the rocks, so your eye rests there before it follows the stream out of the frame.','Acrylic','Gallery-wrapped cotton canvas','Landscape','Realism, Nature',30.00,30.00,1.50,3.20,2024,0,NULL,1,1,65000.00,10.00,58500.00,'INR','SOLD',2,'STUDIO','Vaishali J Goswami',2,NULL,NULL,1,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-24 13:27:29','2026-10-25 15:01:47','2026-10-24 13:27:29','2026-10-25 15:01:47'),(3,'lines-of-a-lifetime','Lines of a Lifetime','A charcoal portrait of an elder, every wrinkle mapped with compressed and vine charcoal.','Faces like this one tell a whole story without a word. I worked from dark to light, lifting highlights with a kneaded eraser to keep the skin soft next to the deep shadows.','Charcoal','Heavyweight cartridge paper','Portrait','Realism, Portrait',28.00,18.00,0.10,0.90,2024,0,NULL,1,1,42000.00,0.00,42000.00,'INR','SOLD',2,'STUDIO','Vaishali J Goswami',2,NULL,NULL,0,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-24 13:27:29','2026-10-25 15:10:54','2026-10-24 13:27:29','2026-10-25 15:10:54'),(4,'curls-in-charcoal','Curls in Charcoal','A portrait study focused on movement in the hair and a calm, direct gaze.','I wanted the curls to feel like they were still moving, so I drew them fast and fixed the face slowly.','Charcoal','Toned drawing paper','Portrait','Expressive, Portrait',14.00,20.00,0.10,0.50,2023,0,NULL,1,1,24000.00,20.00,19200.00,'INR','SOLD',2,'STUDIO','Vaishali J Goswami',2,NULL,NULL,0,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29','2026-10-24 13:27:29','2026-10-24 13:27:29'),(5,'flameback-woodpecker','Flameback Woodpecker','A golden-backed woodpecker clinging to rough bark, painted in transparent watercolour glazes.','The bark took longer than the bird. I used salt and dry-brush to find the texture, then kept the bird crisp so it pops off the trunk.','Watercolour','Cold-press 100% cotton paper','Wildlife','Realism, Nature',20.00,17.50,0.10,0.70,2025,1,'White wood frame with mount',1,1,36000.00,0.00,36000.00,'INR','AVAILABLE',8,'STUDIO','Vaishali J Goswami',2,NULL,NULL,1,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-24 13:27:29',NULL,'2026-10-24 13:27:29','2026-11-05 10:52:00'),(6,'copper-kettle-still-life','Copper Kettle Still Life','An antique kettle on its warmer, drawn with careful attention to reflections on metal.','Reflective objects are my favourite puzzle. Everything you see on the kettle is the room around it, bent and squeezed.','Graphite','Smooth bristol board','Still life','Realism, Study',12.00,16.00,0.10,0.40,2024,0,NULL,1,1,9500.00,5.00,9025.00,'INR','SOLD',2,'STUDIO','Vaishali J Goswami',2,NULL,NULL,0,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-24 13:27:29','2026-10-25 23:31:57','2026-10-24 13:27:29','2026-10-25 23:31:57'),(7,'monsoon-hills','Monsoon Hills','Wet-in-wet sky with layered hills, painted on 300gsm cold-press paper.','Painted during my first ArtistikCity watercolour course. I let the sky bleed into the hills to catch that heavy monsoon feeling.','Watercolour','Cold-press 300gsm paper','Landscape','Student original',11.00,15.00,0.05,0.30,2026,0,NULL,1,1,2500.00,0.00,2500.00,'INR','AVAILABLE',0,'STUDENT','Aarav Sharma',NULL,1,1,0,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-24 13:27:29',NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(8,'glass-marble-light','Glass Marble, Morning Light','A single glass marble and its long shadow, drawn to study refraction.','A small object, a big lesson in light. Submitted for review before it goes live.','Graphite','Smooth bristol board','Still life','Minimal, Study',10.00,14.00,0.10,0.30,2026,0,NULL,1,1,7000.00,0.00,7000.00,'INR','PENDING_REVIEW',0,'STUDIO','Vaishali J Goswami',2,NULL,NULL,0,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(9,'rose-in-monochrome','Rose in Monochrome','A single rose rendered in graphite with deep tonal contrast.',NULL,'Graphite','Archival drawing paper','Botanical','Realism',16.00,12.00,0.10,0.40,2026,0,NULL,1,1,8000.00,0.00,8000.00,'INR','DRAFT',0,'STUDIO','Vaishali J Goswami',2,NULL,NULL,0,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(10,'glass-jars-still-life','Glass Jars on the Sill','Two jars and a coin on a windowsill, a study of transparency and texture.','Drawn on a slow afternoon; the coin in the jar was a last-minute idea that ended up being the focal point.','Graphite','Textured drawing paper','Still life','Realism, Study',12.00,16.00,0.10,0.40,2025,0,NULL,1,1,12000.00,0.00,12000.00,'INR','SOLD',2,'STUDIO','Vaishali J Goswami',2,NULL,NULL,0,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29','2026-10-24 13:27:29','2026-10-24 13:27:29'),(101,'eye-study-in-graphite','Eye Study in Graphite','A hyper-real eye drawn in layered graphite.','My favourite piece from the eye course.','Graphite','Bristol board','Portrait','Realism, Student original',9.00,12.00,0.10,0.50,2026,0,NULL,1,1,3500.00,0.00,3500.00,'INR','AVAILABLE',0,'STUDENT','Ishaan Verma',NULL,101,1001,0,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-09-24 14:22:13',NULL,'2026-09-14 14:22:13','2026-10-24 14:22:13'),(102,'portrait-of-my-grandfather','Portrait of My Grandfather','A charcoal portrait drawn from a family photograph.','Drawing his face felt like listening to his stories again.','Charcoal','Cartridge paper','Portrait','Realism, Student original',16.00,12.00,0.10,0.50,2026,0,NULL,1,1,5200.00,0.00,5200.00,'INR','AVAILABLE',2,'STUDENT','Neha Kulkarni',NULL,102,1002,1,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-09-24 14:22:13',NULL,'2026-09-14 14:22:13','2026-11-05 11:15:00'),(103,'kitchen-jars-ink-study','Kitchen Jars, Ink Study','A loose ink sketch of glass jars on a shelf.',NULL,'Ink','Sketchbook paper','Still life','Sketch, Student original',8.00,11.00,0.10,0.50,2026,0,NULL,1,1,1800.00,0.00,1800.00,'INR','PENDING_REVIEW',0,'STUDENT','Rahul Gupta',NULL,105,1004,0,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-09-14 14:22:13','2026-10-24 14:22:13'),(104,'botanical-apples','Orchard Apples','Three apples on a blue cloth in vivid oil pastel.','I love how complementary colours make each other glow.','Oil pastel','Pastel paper','Still life','Colourful, Still life',12.00,16.00,0.10,0.50,2025,1,'Walnut frame',1,1,14000.00,10.00,12600.00,'INR','SOLD',4,'STUDIO','Ananya Rao',11,NULL,NULL,1,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-09-24 14:22:13','2026-10-25 23:20:08','2026-09-14 14:22:13','2026-10-25 23:20:08'),(105,'himalayan-morning','Himalayan Morning','Snow peaks above a pine forest and a clear river.','Painted after a trek in Himachal.','Acrylic','Stretched canvas','Landscape','Realism, Nature',24.00,30.00,0.10,0.50,2025,0,NULL,1,1,32000.00,0.00,32000.00,'INR','AVAILABLE',4,'STUDIO','Meera Krishnan',13,NULL,NULL,1,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-09-24 14:22:13',NULL,'2026-09-14 14:22:13','2026-10-25 14:56:11'),(106,'sleeping-cat','Sleeping Cat','A soft graphite study of a cat asleep in the sun.',NULL,'Graphite','Drawing paper','Wildlife','Realism, Study',11.00,14.00,0.10,0.50,2024,0,NULL,1,1,8500.00,0.00,8500.00,'INR','SOLD',0,'STUDIO','Kabir Malhotra',12,NULL,NULL,0,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-09-24 14:22:13','2026-10-14 14:22:13','2026-09-14 14:22:13','2026-10-24 14:22:13'),(107,'ember-abstract','Ember','A fiery abstract in poured acrylic and knife work.','Movement first, meaning later.','Acrylic','Wood panel','Abstract','Abstract, Contemporary',20.00,20.00,0.10,0.50,2026,0,NULL,1,1,22000.00,15.00,18700.00,'INR','AVAILABLE',0,'STUDIO','Meera Krishnan',13,NULL,NULL,0,'CANVAS,POSTER',50,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-09-24 14:22:13',NULL,'2026-09-14 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `paintings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `payment_logs`
--

DROP TABLE IF EXISTS `payment_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  `payment_option` varchar(50) DEFAULT NULL,
  `payment_id` varchar(255) DEFAULT NULL,
  `payment_status` tinyint NOT NULL DEFAULT '0',
  `payment_details` longtext,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1032 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `payment_logs`
--

LOCK TABLES `payment_logs` WRITE;
/*!40000 ALTER TABLE `payment_logs` DISABLE KEYS */;
INSERT INTO `payment_logs` VALUES (1001,101,1001,101,'RazorPay','pay_DEMO1001',1,'{\"note\": \"demo data\"}','2026-05-27 14:22:13','2026-05-28 14:22:13'),(1002,101,1002,112,'RazorPay','pay_DEMO1002',1,'{\"note\": \"demo data\"}','2026-06-26 13:22:13','2026-06-27 14:22:13'),(1003,101,1003,121,'RazorPay','pay_DEMO1003',1,'{\"note\": \"demo data\"}','2026-10-04 12:22:13','2026-10-05 14:22:13'),(1005,102,1005,103,'RazorPay','pay_DEMO1005',1,'{\"note\": \"demo data\"}','2026-06-06 10:22:13','2026-06-07 14:22:13'),(1006,102,1006,104,'RazorPay','pay_DEMO1006',1,'{\"note\": \"demo data\"}','2026-07-21 09:22:13','2026-07-22 14:22:13'),(1007,102,1007,122,'RazorPay','pay_DEMO1007',1,'{\"note\": \"demo data\"}','2026-10-09 08:22:13','2026-10-10 14:22:13'),(1008,102,1008,112,'RazorPay','pay_DEMO1008',1,'{\"note\": \"demo data\"}','2026-07-06 14:22:13','2026-07-07 14:22:13'),(1009,103,1009,108,'RazorPay','pay_DEMO1009',1,'{\"note\": \"demo data\"}','2026-06-16 13:22:13','2026-06-17 14:22:13'),(1010,103,1010,107,'RazorPay','pay_DEMO1010',1,'{\"note\": \"demo data\"}','2026-08-25 12:22:13','2026-08-26 14:22:13'),(1011,103,1011,123,'RazorPay','pay_DEMO1011',1,'{\"note\": \"demo data\"}','2026-10-12 11:22:13','2026-10-13 14:22:13'),(1013,104,1013,106,'RazorPay','pay_DEMO1013',1,'{\"note\": \"demo data\"}','2026-07-16 09:22:13','2026-07-17 14:22:13'),(1014,104,1014,110,'RazorPay','pay_DEMO1014',1,'{\"note\": \"demo data\"}','2026-09-09 08:22:13','2026-09-10 14:22:13'),(1015,104,1015,125,'RazorPay','pay_DEMO1015',1,'{\"note\": \"demo data\"}','2026-10-14 14:22:13','2026-10-15 14:22:13'),(1017,105,1017,105,'RazorPay','pay_DEMO1017',1,'{\"note\": \"demo data\"}','2026-07-26 12:22:13','2026-07-27 14:22:13'),(1018,105,1018,109,'RazorPay','pay_DEMO1018',1,'{\"note\": \"demo data\"}','2026-08-15 11:22:13','2026-08-16 14:22:13'),(1019,105,1019,111,'RazorPay','pay_DEMO1019',1,'{\"note\": \"demo data\"}','2026-06-11 10:22:13','2026-06-12 14:22:13'),(1020,105,1020,124,'RazorPay','pay_DEMO1020',3,'{\"note\": \"demo data\"}','2026-09-24 09:22:13','2026-09-25 14:22:13'),(1021,106,1021,110,'PayPal','pay_DEMO1021',1,'{\"note\": \"demo data\"}','2026-08-05 08:22:13','2026-08-06 14:22:13'),(1022,106,1022,111,'PayPal','pay_DEMO1022',1,'{\"note\": \"demo data\"}','2026-06-21 14:22:13','2026-06-22 14:22:13'),(1023,106,1023,126,'PayPal','pay_DEMO1023',1,'{\"note\": \"demo data\"}','2026-09-29 13:22:13','2026-09-30 14:22:13'),(1026,101,1026,102,'RazorPay','pay_DEMO1026',1,'{\"note\": \"demo data\"}','2026-09-14 10:22:13','2026-09-15 14:22:13'),(1027,102,1027,109,'RazorPay','pay_DEMO1027',1,'{\"note\": \"demo data\"}','2026-09-19 09:22:13','2026-09-20 14:22:13'),(1028,103,1028,101,'RazorPay','pay_DEMO1028',1,'{\"note\": \"demo data\"}','2026-08-30 08:22:13','2026-08-31 14:22:13'),(1029,104,1029,107,'RazorPay','pay_DEMO1029',1,'{\"note\": \"demo data\"}','2026-09-26 14:22:13','2026-09-27 14:22:13'),(1030,105,1030,121,'RazorPay','pay_DEMO1030',1,'{\"note\": \"demo data\"}','2026-10-06 13:22:13','2026-10-07 14:22:13'),(1031,1,1031,110,'RazorPay (test)','test_1031_1792833002209',1,'{\"test\":true}','2026-10-24 14:40:02','2026-10-24 14:40:02');
/*!40000 ALTER TABLE `payment_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `portfolio_marketplace`
--

DROP TABLE IF EXISTS `portfolio_marketplace`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `portfolio_marketplace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `submission_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `is_listed_for_sale` tinyint(1) NOT NULL DEFAULT '0',
  `sale_price` decimal(10,2) DEFAULT NULL,
  `currency` varchar(3) NOT NULL DEFAULT 'INR',
  `inventory_count` int NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `portfolio_marketplace_submission_unique` (`submission_id`),
  KEY `portfolio_marketplace_submission_id_index` (`submission_id`),
  KEY `portfolio_marketplace_user_id_index` (`user_id`),
  KEY `portfolio_marketplace_listed_index` (`is_listed_for_sale`),
  CONSTRAINT `portfolio_marketplace_submission_id_foreign` FOREIGN KEY (`submission_id`) REFERENCES `student_submissions` (`id`),
  CONSTRAINT `portfolio_marketplace_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `portfolio_marketplace_chk_1` CHECK (((`sale_price` is null) or (`sale_price` >= 0))),
  CONSTRAINT `portfolio_marketplace_chk_2` CHECK ((`inventory_count` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=1005 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `portfolio_marketplace`
--

LOCK TABLES `portfolio_marketplace` WRITE;
/*!40000 ALTER TABLE `portfolio_marketplace` DISABLE KEYS */;
INSERT INTO `portfolio_marketplace` VALUES (1,1,1,1,2500.00,'INR',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(1001,1001,101,1,3500.00,'INR',1,'2026-07-21 14:22:13','2026-10-24 14:22:13'),(1002,1002,102,1,5200.00,'INR',1,'2026-07-31 14:22:13','2026-11-05 11:15:00'),(1003,1003,104,0,NULL,'INR',1,'2026-08-30 14:22:13','2026-10-24 14:22:13'),(1004,1004,105,1,1800.00,'INR',1,'2026-08-20 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `portfolio_marketplace` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `post_tag`
--

DROP TABLE IF EXISTS `post_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_tag` (
  `post_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  KEY `post_tag_post_id_foreign` (`post_id`),
  KEY `post_tag_tag_id_foreign` (`tag_id`),
  CONSTRAINT `post_tag_post_id_foreign` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`),
  CONSTRAINT `post_tag_tag_id_foreign` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `post_tag`
--

LOCK TABLES `post_tag` WRITE;
/*!40000 ALTER TABLE `post_tag` DISABLE KEYS */;
INSERT INTO `post_tag` VALUES (1,1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(1,2,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,2,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(101,101,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(101,104,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(102,102,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(102,103,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(103,101,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(104,103,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(105,107,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(106,108,'2026-10-24 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `post_tag` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `posts`
--

DROP TABLE IF EXISTS `posts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `posts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(256) NOT NULL,
  `subtitle` varchar(500) NOT NULL DEFAULT '',
  `slug` varchar(100) NOT NULL,
  `body` longtext NOT NULL,
  `status` tinyint DEFAULT NULL,
  `posted_by` bigint DEFAULT NULL,
  `image` varchar(500) DEFAULT NULL,
  `meta_title` varchar(255) DEFAULT NULL,
  `meta_description` varchar(500) DEFAULT NULL,
  `featured` tinyint DEFAULT NULL,
  `like` int DEFAULT NULL,
  `dislike` int DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `posts`
--

LOCK TABLES `posts` WRITE;
/*!40000 ALTER TABLE `posts` DISABLE KEYS */;
INSERT INTO `posts` VALUES (1,'5 Watercolour Mistakes Beginners Make','And how to avoid them','5-watercolour-mistakes-beginners-make','<p>Watercolour rewards planning. Here are the five most common mistakes we see in beginner classes and simple ways to fix them.</p>',1,2,'blog-1.jpg',NULL,NULL,1,0,0,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,'Choosing Your First Set of Brushes','A practical buying guide','choosing-your-first-set-of-brushes','<p>You do not need dozens of brushes. Three good brushes will take you a long way.</p>',1,2,'blog-2.jpg',NULL,NULL,0,0,0,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(3,'Finding Inspiration Every Day','Keep a sketchbook habit','finding-inspiration-every-day','<p>A daily five-minute sketch builds observation skills faster than anything else.</p>',1,2,'blog-3.jpg',NULL,NULL,0,0,0,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(101,'How to Stretch Watercolour Paper','Flat paper, happy paintings: a five-minute routine.','how-to-stretch-watercolour-paper','<p>Buckled paper makes washes pool in the wrong places. Stretching your paper takes five minutes and saves a painting.</p><h2>What you need</h2><ul><li>A board larger than your paper</li><li>Gummed tape</li><li>A clean sponge</li></ul><h2>The steps</h2><ol><li>Wet the paper evenly on both sides.</li><li>Lay it flat on the board.</li><li>Tape all four edges and let it dry flat.</li></ol>',1,11,'demo-101.jpg',NULL,'Flat paper, happy paintings: a five-minute routine.',1,0,0,'2026-08-05 14:22:13','2026-08-15 14:22:13'),(102,'The Five Values Every Portrait Needs','From highlight to core shadow, made simple.','the-five-values-every-portrait-needs','<p>Likeness is mostly about values, not lines. Squint at your reference and find five values: highlight, light, half-tone, core shadow and reflected light.</p><blockquote>If the values are right, the drawing reads from across the room.</blockquote><p>Practise with a simple sphere before moving to faces.</p>',1,12,'demo-102.jpg',NULL,'From highlight to core shadow, made simple.',0,0,0,'2026-08-17 14:22:13','2026-08-27 14:22:13'),(103,'Palette Knife Painting for Beginners','Why a knife can be easier than a brush.','palette-knife-painting-for-beginners','<p>A palette knife forces you to simplify. You cannot fiddle, so you make decisions.</p><h2>Three strokes to learn first</h2><ul><li>The spread</li><li>The scrape</li><li>The dab</li></ul>',1,13,'demo-103.jpg',NULL,'Why a knife can be easier than a brush.',0,0,0,'2026-08-29 14:22:13','2026-09-08 14:22:13'),(104,'Meet Our Students: Neha\'s Portrait Journey','From stick figures to a framed portrait in four months.','meet-our-students-nehas-portrait-journey','<p>Neha joined ArtistikCity in spring with no drawing experience. Four months later her charcoal portrait of her grandfather was approved and listed in the marketplace.</p><p>\"The feedback on every assignment kept me going,\" she says.</p>',1,2,'demo-104.jpg',NULL,'From stick figures to a framed portrait in four months.',1,0,0,'2026-09-10 14:22:13','2026-09-20 14:22:13'),(105,'New Workshops This Month','Loose florals, pet portraits and palette knife sunsets.','new-workshops-this-month','<p>We have three new weekend workshops: <strong>Loose Florals in One Day</strong>, <strong>Pet Portraits in Charcoal</strong> and <strong>Palette Knife Sunset</strong>. Seats are limited to keep the groups small.</p>',1,1,'demo-105.jpg',NULL,'Loose florals, pet portraits and palette knife sunsets.',0,0,0,'2026-09-22 14:22:13','2026-10-02 14:22:13'),(106,'Choosing an iPad for Digital Art','What matters and what does not.','choosing-an-ipad-for-digital-art','<p>Draft: compare screen sizes, pencil support and storage. Add a short buying guide for students.</p>',0,15,'demo-106.jpg',NULL,'What matters and what does not.',0,0,0,'2026-10-04 14:22:13','2026-10-14 14:22:13');
/*!40000 ALTER TABLE `posts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `schema_migrations`
--

DROP TABLE IF EXISTS `schema_migrations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_migrations` (
  `filename` varchar(191) NOT NULL,
  `applied_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`filename`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `schema_migrations`
--

LOCK TABLES `schema_migrations` WRITE;
/*!40000 ALTER TABLE `schema_migrations` DISABLE KEYS */;
INSERT INTO `schema_migrations` VALUES ('2026-10-23_auth_system.sql','2026-10-23 18:47:43'),('2026-10-23_wishlist.sql','2026-10-23 22:25:29'),('2026-10-24_course_checkout.sql','2026-10-23 23:38:43'),('2026-10-25_admin_console.sql','2026-10-24 10:02:48'),('2026-10-26_enrollment_refunded.sql','2026-10-24 10:33:55'),('2026-10-27_course_lesson_videos.sql','2026-10-24 14:19:44'),('2026-10-28_marketplace_shop.sql','2026-10-25 14:54:52'),('2026-11-05_documents_s3.sql','2026-11-05 11:53:58');
/*!40000 ALTER TABLE `schema_migrations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `settings`
--

DROP TABLE IF EXISTS `settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `meta_tag` varchar(100) DEFAULT NULL,
  `meta_key` varchar(100) DEFAULT NULL,
  `meta_value` longtext,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `settings`
--

LOCK TABLES `settings` WRITE;
/*!40000 ALTER TABLE `settings` DISABLE KEYS */;
INSERT INTO `settings` VALUES (1,'home','course_text_1','Live online classes','2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,'home','course_text_2','Personal feedback','2026-10-24 13:27:29','2026-10-24 13:27:29'),(3,'home','course_text_3','Certificate of completion','2026-10-24 13:27:29','2026-10-24 13:27:29'),(4,'home','workshop_text_1','Weekend sessions','2026-10-24 13:27:29','2026-10-24 13:27:29'),(5,'home','workshop_text_2','All materials list provided','2026-10-24 13:27:29','2026-10-24 13:27:29'),(6,'home','workshop_text_3','Small batches','2026-10-24 13:27:29','2026-10-24 13:27:29'),(7,'seo','site_title','ArtistikCity - Live online art classes','2026-10-24 13:27:29','2026-10-24 13:27:29'),(8,'seo','meta_description','Learn to draw and paint online with live classes, personal feedback and certificates. Courses and workshops in watercolour, acrylic, sketching and more.','2026-10-24 13:27:29','2026-10-24 13:27:29'),(9,'seo','meta_keywords','online art classes, painting course, drawing course, watercolour, acrylic','2026-10-24 13:27:29','2026-10-24 13:27:29'),(10,'seo','og_image','','2026-10-24 13:27:29','2026-10-24 13:27:29'),(11,'contact','email','info@artistickcity.com','2026-10-24 13:27:29','2026-10-24 13:27:29'),(12,'contact','phone','9876 543 201','2026-10-24 13:27:29','2026-10-24 13:27:29'),(13,'contact','address','505, Agate, MyHomes Jewel,\nMadinguda, Hyderabad - 540017\nTelanganma, India.','2026-10-24 13:27:29','2026-10-24 13:27:29'),(14,'social','facebook','https://www.facebook.com/StudioVaishaliArts/','2026-10-24 13:27:29','2026-10-24 13:27:29'),(15,'social','instagram','https://www.instagram.com/artistikcity/','2026-10-24 13:27:29','2026-10-24 13:27:29'),(16,'social','youtube','https://www.youtube.com/@artistikcityYT','2026-10-24 13:27:29','2026-10-24 13:27:29'),(17,'social','linkedin','https://in.linkedin.com/in/vaishali-jetly-goswami-4b5874233','2026-10-24 13:27:29','2026-10-24 13:27:29');
/*!40000 ALTER TABLE `settings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `shop_cart_items`
--

DROP TABLE IF EXISTS `shop_cart_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shop_cart_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cart_id` bigint NOT NULL,
  `painting_id` bigint NOT NULL,
  `format` varchar(20) NOT NULL,
  `size_code` varchar(20) NOT NULL DEFAULT '',
  `finish` varchar(20) NOT NULL DEFAULT '',
  `frame` varchar(20) NOT NULL DEFAULT 'NONE',
  `quantity` int NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `shop_cart_items_line_unique` (`cart_id`,`painting_id`,`format`,`size_code`,`finish`,`frame`),
  KEY `shop_cart_items_painting_id_foreign` (`painting_id`),
  CONSTRAINT `shop_cart_items_cart_id_foreign` FOREIGN KEY (`cart_id`) REFERENCES `shop_carts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `shop_cart_items_painting_id_foreign` FOREIGN KEY (`painting_id`) REFERENCES `paintings` (`id`) ON DELETE CASCADE,
  CONSTRAINT `shop_cart_items_chk_1` CHECK ((`format` in (_utf8mb4'ORIGINAL',_utf8mb4'CANVAS',_utf8mb4'POSTER',_utf8mb4'DIGITAL'))),
  CONSTRAINT `shop_cart_items_chk_2` CHECK ((`quantity` between 1 and 10))
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shop_cart_items`
--

LOCK TABLES `shop_cart_items` WRITE;
/*!40000 ALTER TABLE `shop_cart_items` DISABLE KEYS */;
INSERT INTO `shop_cart_items` VALUES (1,1,105,'ORIGINAL','','','NONE',1,'2026-10-25 14:55:48','2026-10-25 14:55:57'),(3,1,5,'POSTER','XL','MATTE','OAK',1,'2026-10-25 14:57:36','2026-10-25 14:57:36'),(10,2,5,'ORIGINAL','','','NONE',1,'2026-11-05 10:36:57','2026-11-05 10:42:02');
/*!40000 ALTER TABLE `shop_cart_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `shop_carts`
--

DROP TABLE IF EXISTS `shop_carts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shop_carts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_key` varchar(80) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `coupon_code` varchar(40) DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `shop_carts_owner_unique` (`owner_key`),
  KEY `shop_carts_user_id_foreign` (`user_id`),
  CONSTRAINT `shop_carts_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shop_carts`
--

LOCK TABLES `shop_carts` WRITE;
/*!40000 ALTER TABLE `shop_carts` DISABLE KEYS */;
INSERT INTO `shop_carts` VALUES (1,'S:cd3d722e-13ea-4a96-9093-4add167991bb',NULL,NULL,'2026-10-25 14:55:48','2026-10-25 14:57:36'),(2,'U:1',1,NULL,'2026-10-25 15:00:41','2026-11-05 10:42:02');
/*!40000 ALTER TABLE `shop_carts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `shop_coupons`
--

DROP TABLE IF EXISTS `shop_coupons`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shop_coupons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(40) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `discount_type` varchar(10) NOT NULL,
  `discount_value` decimal(10,2) NOT NULL,
  `painting_id` bigint DEFAULT NULL,
  `artist_name` varchar(255) DEFAULT NULL,
  `min_subtotal` decimal(10,2) DEFAULT NULL,
  `max_uses` int DEFAULT NULL,
  `used_count` int NOT NULL DEFAULT '0',
  `starts_at` datetime DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `shop_coupons_code_unique` (`code`),
  KEY `shop_coupons_painting_id_foreign` (`painting_id`),
  CONSTRAINT `shop_coupons_painting_id_foreign` FOREIGN KEY (`painting_id`) REFERENCES `paintings` (`id`) ON DELETE CASCADE,
  CONSTRAINT `shop_coupons_chk_1` CHECK ((`discount_type` in (_utf8mb4'PERCENT',_utf8mb4'FLAT'))),
  CONSTRAINT `shop_coupons_chk_2` CHECK ((`discount_value` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shop_coupons`
--

LOCK TABLES `shop_coupons` WRITE;
/*!40000 ALTER TABLE `shop_coupons` DISABLE KEYS */;
INSERT INTO `shop_coupons` VALUES (1,'WELCOME10','10% off your first artwork','PERCENT',10.00,NULL,NULL,NULL,NULL,1,NULL,NULL,1,'2026-10-25 14:54:51','2026-10-25 15:09:56');
/*!40000 ALTER TABLE `shop_coupons` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `shop_order_items`
--

DROP TABLE IF EXISTS `shop_order_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shop_order_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` varchar(50) NOT NULL,
  `painting_id` bigint NOT NULL,
  `format` varchar(20) NOT NULL,
  `size_code` varchar(20) NOT NULL DEFAULT '',
  `finish` varchar(20) NOT NULL DEFAULT '',
  `frame` varchar(20) NOT NULL DEFAULT 'NONE',
  `quantity` int NOT NULL DEFAULT '1',
  `title` varchar(255) NOT NULL,
  `artist_name` varchar(255) NOT NULL,
  `image_url` varchar(2083) DEFAULT NULL,
  `variant_label` varchar(255) NOT NULL,
  `unit_price_inr` decimal(12,2) NOT NULL,
  `line_total_inr` decimal(12,2) NOT NULL,
  `discount_inr` decimal(12,2) NOT NULL DEFAULT '0.00',
  `edition_no` int DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `download_token` varchar(64) DEFAULT NULL,
  `download_count` int NOT NULL DEFAULT '0',
  `download_expires_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `shop_order_items_download_unique` (`download_token`),
  KEY `shop_order_items_order_id_foreign` (`order_id`),
  KEY `shop_order_items_painting_id_foreign` (`painting_id`),
  CONSTRAINT `shop_order_items_order_id_foreign` FOREIGN KEY (`order_id`) REFERENCES `shop_orders` (`order_id`) ON DELETE CASCADE,
  CONSTRAINT `shop_order_items_painting_id_foreign` FOREIGN KEY (`painting_id`) REFERENCES `paintings` (`id`),
  CONSTRAINT `shop_order_items_chk_1` CHECK ((`format` in (_utf8mb4'ORIGINAL',_utf8mb4'CANVAS',_utf8mb4'POSTER',_utf8mb4'DIGITAL'))),
  CONSTRAINT `shop_order_items_chk_2` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'OK',_utf8mb4'REFUND_REQUIRED',_utf8mb4'REFUNDED')))
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shop_order_items`
--

LOCK TABLES `shop_order_items` WRITE;
/*!40000 ALTER TABLE `shop_order_items` DISABLE KEYS */;
INSERT INTO `shop_order_items` VALUES (1,'AC-S-20261025-QS2XY4',2,'ORIGINAL','','','WHITE_FLOAT',1,'The Old Mill at Dawn','Vaishali J Goswami','/assets/images/course1.jpg','Original painting · 30 × 30 in · white canvas float frame',62100.00,62100.00,0.00,NULL,'OK',NULL,0,NULL),(2,'AC-S-20261025-X82HLZ',3,'POSTER','XL','MATTE','OAK',1,'Lines of a Lifetime','Vaishali J Goswami','/assets/images/course2.jpg','Fine art poster · 23 × 36 in · matte · natural oak frame',6003.00,6003.00,600.00,NULL,'OK',NULL,0,NULL),(3,'AC-S-20261025-DXNB9U',3,'ORIGINAL','','','NONE',1,'Lines of a Lifetime','Vaishali J Goswami','/assets/images/course2.jpg','Original painting · 18 × 28 in',42000.00,42000.00,0.00,NULL,'OK',NULL,0,NULL),(4,'AC-S-20261025-DDYWPF',104,'ORIGINAL','','','NONE',1,'Orchard Apples','Ananya Rao','/assets/images/home-category-image-3.jpg','Original painting · 16 × 12 in',12600.00,12600.00,0.00,NULL,'OK',NULL,0,NULL),(5,'AC-S-20261025-4AXTZU',6,'ORIGINAL','','','NONE',1,'Copper Kettle Still Life','Vaishali J Goswami','/assets/images/portfolio-5.jpg','Original painting · 16 × 12 in',9025.00,9025.00,0.00,NULL,'OK',NULL,0,NULL);
/*!40000 ALTER TABLE `shop_order_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `shop_orders`
--

DROP TABLE IF EXISTS `shop_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shop_orders` (
  `order_id` varchar(50) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `owner_key` varchar(80) NOT NULL,
  `buyer_name` varchar(255) NOT NULL,
  `buyer_email` varchar(255) NOT NULL,
  `buyer_phone` varchar(50) DEFAULT NULL,
  `address_line1` varchar(255) DEFAULT NULL,
  `address_line2` varchar(255) DEFAULT NULL,
  `city` varchar(100) DEFAULT NULL,
  `state` varchar(100) DEFAULT NULL,
  `postal_code` varchar(20) DEFAULT NULL,
  `country` varchar(100) NOT NULL,
  `needs_shipping` tinyint(1) NOT NULL DEFAULT '1',
  `gateway` varchar(20) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `subtotal_inr` decimal(12,2) NOT NULL,
  `discount_inr` decimal(12,2) NOT NULL DEFAULT '0.00',
  `shipping_inr` decimal(12,2) NOT NULL DEFAULT '0.00',
  `total_inr` decimal(12,2) NOT NULL,
  `refund_due_inr` decimal(12,2) NOT NULL DEFAULT '0.00',
  `coupon_code` varchar(40) DEFAULT NULL,
  `provider_ref` varchar(255) DEFAULT NULL,
  `transaction_id` varchar(255) DEFAULT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'PAYMENT_PENDING',
  `courier` varchar(100) DEFAULT NULL,
  `tracking_number` varchar(100) DEFAULT NULL,
  `staff_notes` longtext,
  `paid_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`order_id`),
  KEY `shop_orders_user_id_foreign` (`user_id`),
  KEY `shop_orders_status_index` (`status`),
  KEY `shop_orders_provider_ref_index` (`provider_ref`),
  KEY `shop_orders_owner_index` (`owner_key`),
  CONSTRAINT `shop_orders_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `shop_orders_chk_1` CHECK ((`gateway` in (_utf8mb4'RAZORPAY',_utf8mb4'PAYPAL',_utf8mb4'TEST'))),
  CONSTRAINT `shop_orders_chk_2` CHECK ((`status` in (_utf8mb4'PAYMENT_PENDING',_utf8mb4'PAID',_utf8mb4'PACKED',_utf8mb4'SHIPPED',_utf8mb4'DELIVERED',_utf8mb4'CANCELLED',_utf8mb4'EXPIRED',_utf8mb4'REFUND_REQUIRED',_utf8mb4'REFUNDED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shop_orders`
--

LOCK TABLES `shop_orders` WRITE;
/*!40000 ALTER TABLE `shop_orders` DISABLE KEYS */;
INSERT INTO `shop_orders` VALUES ('AC-S-20261025-4AXTZU',1,'U:1','Aarav Sharma','student@artistikcity.com','+919876543210','12 MG Road',NULL,'Mumbai','Maharashtra','400001','India',1,'TEST','INR',9025.00,9025.00,0.00,0.00,9025.00,0.00,NULL,NULL,'TEST-1792951317414','PAID',NULL,NULL,NULL,'2026-10-25 23:31:57','2026-10-25 23:31:56','2026-10-25 23:31:57'),('AC-S-20261025-DDYWPF',1,'U:1','Aarav Sharma','student@artistikcity.com','+919876543210','12 MG Road',NULL,'Mumbai','Maharashtra','400001','India',1,'TEST','INR',12600.00,12600.00,0.00,0.00,12600.00,0.00,NULL,NULL,'TEST-1792950607805','PAID',NULL,NULL,NULL,'2026-10-25 23:20:08','2026-10-25 23:20:07','2026-10-25 23:20:08'),('AC-S-20261025-DXNB9U',1,'U:1','Aarav Sharma','student@artistikcity.com','+919876543210','MYHOME JEWELS, AGATE 1010, MADEENAGUDA, MIYAPUR',NULL,'Hyderabad','Telangana','500049','India',1,'TEST','INR',42000.00,42000.00,0.00,0.00,42000.00,0.00,NULL,NULL,'TEST-1792921254187','PAID',NULL,NULL,NULL,'2026-10-25 15:10:54','2026-10-25 15:10:53','2026-10-25 15:10:54'),('AC-S-20261025-QS2XY4',1,'U:1','Aarav Sharma','student@artistikcity.com','+919876543210','MYHOME JEWELS, AGATE 1010, MADEENAGUDA, MIYAPUR',NULL,'Hyderabad','Telangana','500049','India',1,'TEST','INR',62100.00,62100.00,0.00,0.00,62100.00,0.00,NULL,NULL,'TEST-1792920706583','PACKED',NULL,NULL,NULL,'2026-10-25 15:01:47','2026-10-25 15:01:45','2026-10-25 15:03:10'),('AC-S-20261025-X82HLZ',1,'U:1','Aarav Sharma','student@artistikcity.com','+919876543210','MYHOME JEWELS, AGATE 1010, MADEENAGUDA, MIYAPUR',NULL,'Hyderabad','Telangana','500049','India',1,'TEST','INR',5552.00,6003.00,600.00,149.00,5552.00,0.00,'WELCOME10',NULL,'TEST-1792921196352','PAID',NULL,NULL,NULL,'2026-10-25 15:09:56','2026-10-25 15:09:55','2026-10-25 15:09:56');
/*!40000 ALTER TABLE `shop_orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `shop_price_options`
--

DROP TABLE IF EXISTS `shop_price_options`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shop_price_options` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kind` varchar(20) NOT NULL,
  `code` varchar(40) NOT NULL,
  `label` varchar(120) NOT NULL,
  `long_side_in` decimal(6,2) DEFAULT NULL,
  `price_inr` decimal(10,2) NOT NULL,
  `extra_inr` decimal(10,2) NOT NULL DEFAULT '0.00',
  `sort_order` int NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `shop_price_options_kind_code_unique` (`kind`,`code`),
  CONSTRAINT `shop_price_options_chk_1` CHECK ((`kind` in (_utf8mb4'CANVAS',_utf8mb4'POSTER',_utf8mb4'DIGITAL',_utf8mb4'FRAME',_utf8mb4'SHIPPING'))),
  CONSTRAINT `shop_price_options_chk_2` CHECK ((`price_inr` >= 0)),
  CONSTRAINT `shop_price_options_chk_3` CHECK ((`extra_inr` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shop_price_options`
--

LOCK TABLES `shop_price_options` WRITE;
/*!40000 ALTER TABLE `shop_price_options` DISABLE KEYS */;
INSERT INTO `shop_price_options` VALUES (1,'CANVAS','S','Small',12.00,2499.00,600.00,1,1,'2026-10-25 14:54:51'),(2,'CANVAS','M','Medium',24.00,4999.00,1100.00,2,1,'2026-10-25 14:54:51'),(3,'CANVAS','L','Large',36.00,8999.00,1600.00,3,1,'2026-10-25 14:54:51'),(4,'CANVAS','XL','Statement',48.00,13999.00,2200.00,4,1,'2026-10-25 14:54:51'),(5,'POSTER','S','Small',12.00,799.00,0.00,1,1,'2026-10-25 14:54:51'),(6,'POSTER','M','Medium',18.00,1199.00,0.00,2,1,'2026-10-25 14:54:51'),(7,'POSTER','L','Large',24.00,1699.00,0.00,3,1,'2026-10-25 14:54:51'),(8,'POSTER','XL','Extra large',36.00,2699.00,0.00,4,1,'2026-10-25 14:54:51'),(9,'DIGITAL','PERSONAL','High-resolution file, personal-use licence',NULL,999.00,0.00,1,1,'2026-10-25 14:54:51'),(10,'FRAME','BLACK_WOOD','Black wood frame',NULL,22.00,0.00,1,1,'2026-10-25 14:54:51'),(11,'FRAME','OAK','Natural oak frame',NULL,28.00,0.00,2,1,'2026-10-25 14:54:51'),(12,'FRAME','WHITE_FLOAT','White canvas float frame',NULL,30.00,0.00,3,1,'2026-10-25 14:54:51'),(13,'SHIPPING','PRINTS_INDIA','Prints shipping within India (per order)',NULL,149.00,0.00,1,1,'2026-10-25 14:54:51'),(14,'SHIPPING','PRINTS_INTL','Prints shipping worldwide (per order)',NULL,1999.00,0.00,2,1,'2026-10-25 14:54:51');
/*!40000 ALTER TABLE `shop_price_options` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `skills`
--

DROP TABLE IF EXISTS `skills`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `skills` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `skill_slug` varchar(100) NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `skills`
--

LOCK TABLES `skills` WRITE;
/*!40000 ALTER TABLE `skills` DISABLE KEYS */;
INSERT INTO `skills` VALUES (1,'Beginner','beginner',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,'Intermediate','intermediate',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(3,'Advanced','advanced',1,'2026-10-24 13:27:29','2026-10-24 13:27:29');
/*!40000 ALTER TABLE `skills` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_artworks`
--

DROP TABLE IF EXISTS `student_artworks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_artworks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `photo_name` varchar(500) NOT NULL DEFAULT '',
  `comments` longtext,
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=105 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_artworks`
--

LOCK TABLES `student_artworks` WRITE;
/*!40000 ALTER TABLE `student_artworks` DISABLE KEYS */;
INSERT INTO `student_artworks` VALUES (1,1,'artwork.jpg','My first watercolour landscape',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(101,101,'artwork.jpg','My eye study, week 2',1,'2026-10-04 14:22:13','2026-10-24 14:22:13'),(102,102,'artwork.jpg','Hair practice',1,'2026-10-05 14:22:13','2026-10-24 14:22:13'),(103,104,'artwork.jpg','First pour!',1,'2026-10-06 14:22:13','2026-10-24 14:22:13'),(104,105,'artwork.jpg','Kettle with reflections',1,'2026-10-07 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `student_artworks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_documents`
--

DROP TABLE IF EXISTS `student_documents`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `kind` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `course_id` bigint DEFAULT NULL,
  `certificate_no` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `storage_key` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'application/pdf',
  `size_bytes` bigint NOT NULL DEFAULT '0',
  `options` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `student_documents_certificate_no_unique` (`certificate_no`),
  KEY `student_documents_user_kind_index` (`user_id`,`kind`,`created_at`),
  CONSTRAINT `student_documents_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_documents`
--

LOCK TABLES `student_documents` WRITE;
/*!40000 ALTER TABLE `student_documents` DISABLE KEYS */;
INSERT INTO `student_documents` VALUES (1,1,'CERTIFICATE',1,'AC-2026-VQ7F382N','private/documents/certificates/AC-2026-VQ7F382N.pdf','ArtistikCity-certificate-watercolour-landscapes-for-beginners-AC-2026-VQ7F382N.pdf','application/pdf',26839,NULL,'2026-11-05 11:57:02','2026-11-05 11:57:02'),(2,1,'PORTFOLIO',NULL,NULL,'private/documents/portfolios/1/20261105-115716-1670e23f542b.pdf','ArtistikCity-portfolio-aarav-sharma-2026-11-05.pdf','application/pdf',60501,'approved,curriculum,comments,courses:1+110','2026-11-05 11:57:17','2026-11-05 11:57:17');
/*!40000 ALTER TABLE `student_documents` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_submissions`
--

DROP TABLE IF EXISTS `student_submissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_submissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `title` varchar(200) NOT NULL,
  `description` longtext,
  `file_url` varchar(2083) NOT NULL,
  `admin_status` varchar(20) NOT NULL DEFAULT 'Pending Review',
  `reviewer_feedback` longtext,
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `student_submissions_reviewed_by_foreign` (`reviewed_by`),
  KEY `student_submissions_user_id_index` (`user_id`),
  KEY `student_submissions_course_id_index` (`course_id`),
  KEY `student_submissions_admin_status_index` (`admin_status`),
  CONSTRAINT `student_submissions_course_id_foreign` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `student_submissions_reviewed_by_foreign` FOREIGN KEY (`reviewed_by`) REFERENCES `admins` (`id`),
  CONSTRAINT `student_submissions_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `student_submissions_chk_1` CHECK ((`admin_status` in (_utf8mb4'Pending Review',_utf8mb4'Approved',_utf8mb4'Rejected')))
) ENGINE=InnoDB AUTO_INCREMENT=1010 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_submissions`
--

LOCK TABLES `student_submissions` WRITE;
/*!40000 ALTER TABLE `student_submissions` DISABLE KEYS */;
INSERT INTO `student_submissions` VALUES (1,1,1,'Monsoon Hills','Wet-in-wet sky with layered hills, painted on 300gsm cold-press paper.','/storage/uploads/artworks/1/artwork.jpg','Approved','Beautiful control of the washes and a clear sense of depth. Next time, leave a little more white paper for the highlights on the water.',2,'2026-10-24 13:27:29','2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,1,1,'Evening Lake Study','Quick study of reflections at sunset.','/storage/uploads/home-artworks/3/artwork.jpg','Pending Review',NULL,NULL,NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(1001,101,112,'Eye Study in Graphite','Six hours of layering to get the wet look of the eye.','/assets/images/home-category-image-1.jpg','Approved','The reflection in the iris is superb. Soften the lower lashes a little.',2,'2026-07-18 14:22:13','2026-07-16 14:22:13','2026-07-18 14:22:13'),(1002,102,112,'Portrait of My Grandfather','My first full portrait, drawn from an old photograph.','/assets/images/course2.jpg','Approved','Wonderful sensitivity in the wrinkles. Strong values throughout.',2,'2026-07-28 14:22:13','2026-07-26 14:22:13','2026-07-28 14:22:13'),(1003,104,106,'Ember Flow','Acrylic pour with palette-knife accents.','/assets/images/home-category-image-6.jpg','Approved','The colour harmony is lovely and the cells are well controlled.',13,'2026-08-27 14:22:13','2026-08-25 14:22:13','2026-08-27 14:22:13'),(1004,105,111,'Kitchen Jars, Ink Study','Quick ink sketch of my kitchen shelf.','/assets/images/portfolio-3.jpg','Approved','Confident lines. Try varying line weight for depth.',15,'2026-08-17 14:22:13','2026-08-15 14:22:13','2026-08-17 14:22:13'),(1005,106,126,'Teapot Lettering Piece','Lettered quote around a vintage teapot.','/assets/images/portfolio-1.jpg','Pending Review',NULL,NULL,NULL,'2026-10-20 14:22:13','2026-10-22 14:22:13'),(1006,103,108,'Forest Mill Underpainting','Tonal underpainting before colour.','/assets/images/course1.jpg','Pending Review',NULL,NULL,NULL,'2026-10-21 14:22:13','2026-10-23 14:22:13'),(1007,101,101,'Passion Flower Plate','Botanical study of a passion flower.','/storage/uploads/courses/101/cover.jpg','Pending Review',NULL,NULL,NULL,'2026-10-22 14:22:13','2026-10-24 14:22:13'),(1008,102,103,'Curls Study','Charcoal hair study.','/assets/images/course3.jpg','Rejected','Good start, but the proportions of the face drift. Please re-check the eye line and resubmit.',12,'2026-10-06 14:22:13','2026-10-04 14:22:13','2026-10-06 14:22:13'),(1009,105,109,'Copper Kettle','Still life drawing with reflections.','/assets/images/portfolio-5.jpg','Pending Review',NULL,NULL,NULL,'2026-10-23 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `student_submissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_task_photos`
--

DROP TABLE IF EXISTS `student_task_photos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_task_photos` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `student_task_id` bigint NOT NULL,
  `photo_name` varchar(500) NOT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `student_task_photos_task_id_foreign` (`task_id`),
  CONSTRAINT `student_task_photos_task_id_foreign` FOREIGN KEY (`task_id`) REFERENCES `course_module_lesson_tasks` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_task_photos`
--

LOCK TABLES `student_task_photos` WRITE;
/*!40000 ALTER TABLE `student_task_photos` DISABLE KEYS */;
/*!40000 ALTER TABLE `student_task_photos` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_tasks`
--

DROP TABLE IF EXISTS `student_tasks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `student_teacher_connect_id` bigint NOT NULL,
  `from_type` varchar(20) NOT NULL,
  `from_id` bigint NOT NULL,
  `to_type` varchar(20) NOT NULL,
  `to_id` bigint NOT NULL,
  `reply` longtext,
  `review_status` varchar(20) NOT NULL,
  `review_date` datetime DEFAULT NULL,
  `deleted_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `student_tasks_task_id_foreign` (`task_id`),
  KEY `student_tasks_connect_id_foreign` (`student_teacher_connect_id`),
  CONSTRAINT `student_tasks_connect_id_foreign` FOREIGN KEY (`student_teacher_connect_id`) REFERENCES `student_teacher_connects` (`id`),
  CONSTRAINT `student_tasks_task_id_foreign` FOREIGN KEY (`task_id`) REFERENCES `course_module_lesson_tasks` (`id`),
  CONSTRAINT `student_tasks_chk_1` CHECK ((`from_type` in (_utf8mb4'Student',_utf8mb4'Teacher'))),
  CONSTRAINT `student_tasks_chk_2` CHECK ((`to_type` in (_utf8mb4'Student',_utf8mb4'Teacher'))),
  CONSTRAINT `student_tasks_chk_3` CHECK ((`review_status` in (_utf8mb4'Under Review',_utf8mb4'Reviewed',_utf8mb4'Feedback',_utf8mb4'Completed')))
) ENGINE=InnoDB AUTO_INCREMENT=10041 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_tasks`
--

LOCK TABLES `student_tasks` WRITE;
/*!40000 ALTER TABLE `student_tasks` DISABLE KEYS */;
INSERT INTO `student_tasks` VALUES (10010,10102,1001,'Student',101,'Teacher',11,'Here is my structure study of a hibiscus. The petals look flat, any tips?','Under Review','2026-10-18 14:22:13',NULL,'2026-10-18 14:22:13','2026-10-18 14:22:13'),(10020,10302,1002,'Student',102,'Teacher',12,'Uploaded my value scale and a Loomis head. I struggled with the jaw line.','Feedback','2026-10-12 14:22:13',NULL,'2026-10-12 14:22:13','2026-10-12 14:22:13'),(10021,10302,1002,'Teacher',12,'Student',102,'<p>Great value scale! For the jaw, check the angle from the ear to the chin with your pencil held at arm\'s length.</p>','Feedback','2026-10-13 14:22:13',NULL,'2026-10-13 14:22:13','2026-10-13 14:22:13'),(10022,10302,1002,'Student',102,'Teacher',12,'Thank you! I redrew it, attached the new version.','Under Review','2026-10-22 14:22:13',NULL,'2026-10-22 14:22:13','2026-10-22 14:22:13'),(10030,10802,1003,'Student',103,'Teacher',14,'My tonal underpainting of the mill. Is the value range wide enough?','Completed','2026-10-04 14:22:13',NULL,'2026-10-04 14:22:13','2026-10-04 14:22:13'),(10031,10802,1003,'Teacher',14,'Student',103,'<p>Yes, lovely range. Keep the darkest darks for the doorway and the water under the bridge.</p>','Completed','2026-10-05 14:22:13',NULL,'2026-10-05 14:22:13','2026-10-05 14:22:13'),(10040,10502,1004,'Student',105,'Teacher',2,'My son drew this parrot in class 3. He is very proud!','Under Review','2026-10-23 14:22:13',NULL,'2026-10-23 14:22:13','2026-10-23 14:22:13');
/*!40000 ALTER TABLE `student_tasks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_teacher_connects`
--

DROP TABLE IF EXISTS `student_teacher_connects`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_teacher_connects` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `teacher_id` bigint NOT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `stc_task_id_foreign` (`task_id`),
  KEY `stc_student_id_foreign` (`student_id`),
  KEY `stc_teacher_id_foreign` (`teacher_id`),
  CONSTRAINT `stc_student_id_foreign` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`),
  CONSTRAINT `stc_task_id_foreign` FOREIGN KEY (`task_id`) REFERENCES `course_module_lesson_tasks` (`id`),
  CONSTRAINT `stc_teacher_id_foreign` FOREIGN KEY (`teacher_id`) REFERENCES `admins` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1005 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_teacher_connects`
--

LOCK TABLES `student_teacher_connects` WRITE;
/*!40000 ALTER TABLE `student_teacher_connects` DISABLE KEYS */;
INSERT INTO `student_teacher_connects` VALUES (1001,10102,101,11,'2026-10-18 14:22:13','2026-10-18 14:22:13'),(1002,10302,102,12,'2026-10-12 14:22:13','2026-10-22 14:22:13'),(1003,10802,103,14,'2026-10-04 14:22:13','2026-10-05 14:22:13'),(1004,10502,105,2,'2026-10-23 14:22:13','2026-10-23 14:22:13');
/*!40000 ALTER TABLE `student_teacher_connects` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tags`
--

DROP TABLE IF EXISTS `tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `slug` varchar(255) NOT NULL,
  `status` tinyint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=109 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tags`
--

LOCK TABLES `tags` WRITE;
/*!40000 ALTER TABLE `tags` DISABLE KEYS */;
INSERT INTO `tags` VALUES (1,'watercolour','watercolour',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,'beginners','beginners',1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(101,'acrylic','acrylic',1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(102,'charcoal','charcoal',1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(103,'portraits','portraits',1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(104,'materials','materials',1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(105,'sketchbook','sketchbook',1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(106,'colour theory','colour-theory',1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(107,'kids','kids',1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(108,'digital art','digital-art',1,'2026-10-24 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `tags` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `testimonials`
--

DROP TABLE IF EXISTS `testimonials`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `testimonials` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `slug` varchar(255) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `photo` varchar(500) DEFAULT NULL,
  `description` longtext,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `testimonials`
--

LOCK TABLES `testimonials` WRITE;
/*!40000 ALTER TABLE `testimonials` DISABLE KEYS */;
INSERT INTO `testimonials` VALUES (1,'Priya Nair','priya-nair','Watercolour student','photo.jpg','The classes are structured beautifully and the feedback on every assignment helped me improve quickly.',0,'2026-10-24 13:27:29','2026-10-25 15:24:59'),(2,'Rohan Mehta','rohan-mehta','Portrait workshop','photo.jpg','I finally understand proportions. My portraits look like the people I am painting!',0,'2026-10-24 13:27:29','2026-10-25 15:25:04'),(101,'Ishaan Verma','ishaan-verma','Eye & feature studies','avatar.png','I never thought I could draw a realistic eye. The step-by-step lessons and Vaishali\'s feedback made it click.',0,'2026-08-25 14:22:13','2026-10-25 15:24:56'),(102,'Neha Kulkarni','neha-kulkarni','Charcoal portrait mastery','avatar.png','Kabir\'s classes changed how I see faces. My grandfather\'s portrait is now framed in our living room.',0,'2026-08-30 14:22:13','2026-10-25 15:24:57'),(103,'Arjun Menon','arjun-menon','Oil painting foundations','avatar.png','Clear, patient teaching. The underpainting method finally made oils feel manageable.',0,'2026-09-04 14:22:13','2026-10-25 15:25:02'),(104,'Sophia Fernandes','sophia-fernandes','Abstract acrylic pouring','avatar.png','So much fun! I sold my first painting through the marketplace two months after the course.',0,'2026-09-09 14:22:13','2026-10-25 15:24:55'),(105,'Rahul Gupta','rahul-gupta','Drawing fundamentals','avatar.png','At 45 I was sure I had no talent. Now I sketch every evening and love it.',0,'2026-09-14 14:22:13','2026-10-25 15:25:00'),(106,'Emily Clarke','emily-clarke','Digital illustration','avatar.png','Taking live classes from London was easy. Sana\'s Procreate tips saved me hours.',1,'2026-09-19 14:22:13','2026-10-25 15:25:08');
/*!40000 ALTER TABLE `testimonials` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_addresses`
--

DROP TABLE IF EXISTS `user_addresses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_addresses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `address_name` varchar(255) NOT NULL,
  `address` varchar(255) NOT NULL,
  `city` varchar(255) DEFAULT NULL,
  `state` varchar(255) DEFAULT NULL,
  `country` varchar(255) DEFAULT NULL,
  `zipcode` varchar(255) DEFAULT NULL,
  `primary` tinyint DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_addresses`
--

LOCK TABLES `user_addresses` WRITE;
/*!40000 ALTER TABLE `user_addresses` DISABLE KEYS */;
INSERT INTO `user_addresses` VALUES (1,1,'Aarav Sharma','12 MG Road','Mumbai','Maharashtra','India','400001',1,1,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(101,101,'Ishaan Verma','10 Civil Lines','Jaipur','Rajasthan','India','302001',1,1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(102,102,'Neha Kulkarni','17 FC Road','Pune','Maharashtra','India','411004',1,1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(103,103,'Arjun Menon','24 Marine Drive','Kochi','Kerala','India','682011',1,1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(104,104,'Sophia Fernandes','31 Rua de Ourem','Panaji','Goa','India','403001',1,1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(105,105,'Rahul Gupta','38 Hazratganj','Lucknow','Uttar Pradesh','India','226001',1,1,'2026-10-24 14:22:13','2026-10-24 14:22:13'),(106,106,'Emily Clarke','45 Baker Street','London',NULL,'United Kingdom','SW1A 1AA',1,1,'2026-10-24 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `user_addresses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `slug` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `email_verified_at` datetime DEFAULT NULL,
  `phone_number` varchar(20) DEFAULT NULL,
  `phone_verified_at` datetime DEFAULT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `is_verified` tinyint(1) NOT NULL DEFAULT '0',
  `webauthn_user_handle` varchar(100) DEFAULT NULL,
  `last_login_at` datetime DEFAULT NULL,
  `profile_title` varchar(255) DEFAULT NULL,
  `profile_description` longtext,
  `profile_photo` varchar(1000) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `notifications` tinyint DEFAULT NULL,
  `language_preference` varchar(50) DEFAULT NULL,
  `free_courses` tinyint NOT NULL DEFAULT '0',
  `free_course_type` varchar(20) NOT NULL DEFAULT 'default',
  `status` tinyint NOT NULL DEFAULT '1',
  `remember_token` varchar(100) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `users_email_unique` (`email`),
  UNIQUE KEY `users_phone_number_unique` (`phone_number`),
  UNIQUE KEY `users_webauthn_user_handle_unique` (`webauthn_user_handle`),
  CONSTRAINT `users_chk_1` CHECK ((`free_course_type` in (_utf8mb4'color',_utf8mb4'black-white',_utf8mb4'default')))
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'Aarav Sharma','aarav-sharma','student@artistikcity.com','2026-10-24 13:27:29','+919876543210','2026-10-24 13:27:29','$2y$12$VqWvJQy.5d1Froy56bhK7.UYJJR3P1xSVH0Fn/ENCn/Deajkz3nh.',1,NULL,'2026-11-05 10:15:34','Aspiring watercolour artist','I love painting landscapes and learning new techniques.','student.jpg','Mumbai, India',1,'English',1,'color',1,NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(2,'Diya Patel','diya-patel','diya@example.com','2026-10-24 13:27:29',NULL,NULL,'$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e',1,NULL,NULL,'Hobbyist',NULL,NULL,'Delhi, India',1,'English',1,'default',1,NULL,'2026-10-24 13:27:29','2026-10-24 13:27:29'),(101,'Ishaan Verma','ishaan-verma','ishaan@example.com','2026-05-27 14:22:13','+919811000101','2026-05-27 14:22:13','$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e',1,NULL,NULL,'Weekend watercolourist','Weekend watercolourist. Learning with ArtistikCity since this year.','avatar.png','Jaipur, India',1,'English',1,'color',1,NULL,'2026-05-27 14:22:13','2026-10-24 14:22:13'),(102,'Neha Kulkarni','neha-kulkarni','neha@example.com','2026-06-08 14:22:13','+919811000102','2026-06-08 14:22:13','$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e',1,NULL,NULL,'Portraits in charcoal','Portraits in charcoal. Learning with ArtistikCity since this year.','avatar.png','Pune, India',1,'English',1,'color',1,NULL,'2026-06-08 14:22:13','2026-10-24 14:22:13'),(103,'Arjun Menon','arjun-menon','arjun@example.com','2026-06-20 14:22:13','+919811000103','2026-06-20 14:22:13','$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e',1,NULL,NULL,'Landscape lover','Landscape lover. Learning with ArtistikCity since this year.','avatar.png','Kochi, India',1,'English',1,'color',1,NULL,'2026-06-20 14:22:13','2026-10-24 14:22:13'),(104,'Sophia Fernandes','sophia-fernandes','sophia@example.com','2026-07-02 14:22:13','+919811000104','2026-07-02 14:22:13','$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e',1,NULL,NULL,'Abstract acrylics','Abstract acrylics. Learning with ArtistikCity since this year.','avatar.png','Panaji, India',1,'English',1,'color',1,NULL,'2026-07-02 14:22:13','2026-10-24 14:22:13'),(105,'Rahul Gupta','rahul-gupta','rahul@example.com','2026-07-14 14:22:13','+919811000105','2026-07-14 14:22:13','$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e',1,NULL,NULL,'Learning to draw at 45','Learning to draw at 45. Learning with ArtistikCity since this year.','avatar.png','Lucknow, India',1,'English',1,'color',1,NULL,'2026-07-14 14:22:13','2026-10-24 14:22:13'),(106,'Emily Clarke','emily-clarke','emily@example.com','2026-07-26 14:22:13','+447700900106','2026-07-26 14:22:13','$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e',1,NULL,NULL,'Digital illustrator','Digital illustrator. Learning with ArtistikCity since this year.','avatar.png','London, United Kingdom',1,'English',1,'color',1,NULL,'2026-07-26 14:22:13','2026-10-24 14:22:13');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `webauthn_credentials`
--

DROP TABLE IF EXISTS `webauthn_credentials`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `webauthn_credentials` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `credential_id` varchar(512) NOT NULL,
  `public_key_cose` blob NOT NULL,
  `algorithm` int NOT NULL,
  `sign_count` bigint NOT NULL DEFAULT '0',
  `transports` varchar(100) DEFAULT NULL,
  `aaguid` char(36) DEFAULT NULL,
  `backup_eligible` tinyint(1) NOT NULL DEFAULT '0',
  `backed_up` tinyint(1) NOT NULL DEFAULT '0',
  `name` varchar(100) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_used_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `webauthn_credentials_id_unique` (`credential_id`),
  KEY `webauthn_credentials_user_id_foreign` (`user_id`),
  CONSTRAINT `webauthn_credentials_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `webauthn_credentials`
--

LOCK TABLES `webauthn_credentials` WRITE;
/*!40000 ALTER TABLE `webauthn_credentials` DISABLE KEYS */;
/*!40000 ALTER TABLE `webauthn_credentials` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wishlist_items`
--

DROP TABLE IF EXISTS `wishlist_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wishlist_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `item_type` varchar(20) NOT NULL,
  `item_id` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `wishlist_items_unique` (`user_id`,`item_type`,`item_id`),
  CONSTRAINT `wishlist_items_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `wishlist_items_chk_1` CHECK ((`item_type` in (_utf8mb4'course',_utf8mb4'painting')))
) ENGINE=InnoDB AUTO_INCREMENT=1007 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wishlist_items`
--

LOCK TABLES `wishlist_items` WRITE;
/*!40000 ALTER TABLE `wishlist_items` DISABLE KEYS */;
INSERT INTO `wishlist_items` VALUES (1000,101,'course',103,'2026-10-19 14:22:13'),(1001,101,'painting',105,'2026-10-18 14:22:13'),(1002,102,'course',110,'2026-10-17 14:22:13'),(1003,103,'course',121,'2026-10-16 14:22:13'),(1004,104,'painting',107,'2026-10-15 14:22:13'),(1005,106,'course',108,'2026-10-14 14:22:13'),(1006,105,'course',104,'2026-10-13 14:22:13');
/*!40000 ALTER TABLE `wishlist_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'artistikcity'
--

--
-- Final view structure for view `courses_workshops`
--

/*!50001 DROP VIEW IF EXISTS `courses_workshops`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `courses_workshops` AS select `c`.`id` AS `id`,`c`.`title` AS `title`,`c`.`introduction` AS `description`,(select `p`.`photo_name` from `course_photos` `p` where (`p`.`course_id` = `c`.`id`) order by `p`.`id` limit 1) AS `thumbnail_url`,lower(`ct`.`course_type_slug`) AS `type`,(select `cp`.`price_inr` from `course_prices` `cp` where (`cp`.`course_id` = `c`.`id`) order by `cp`.`id` limit 1) AS `price`,`c`.`course_start_date` AS `schedule_date` from (`courses` `c` join `course_types` `ct` on((`ct`.`id` = `c`.`course_type_id`))) */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-05 13:40:18
