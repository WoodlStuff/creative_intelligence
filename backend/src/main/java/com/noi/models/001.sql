-- ================================================
-- init script to generate basic schema.
-- ================================================

--drop database if exists nfttext;
create database noi;
CREATE USER 'noi'@'localhost' IDENTIFIED BY 'iBeSmart';
GRANT ALL ON noi.* TO 'noi'@'localhost';
use noi;

-- ============================
-- prompts
-- ============================
DROP TABLE if exists `ai_prompts`;
CREATE TABLE `ai_prompts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `prompt` text NOT NULL,
  `prompt_type` tinyint NOT NULL default 0,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_nlp_requests`;
CREATE TABLE `ai_nlp_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` varchar(65) NOT NULL,
  `ai_prompt_id` bigint NOT NULL,
  `model_name` varchar(50) NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY(ai_prompt_id) references ai_prompts(id) ON DELETE RESTRICT,
  UNIQUE KEY (`uuid`),
  KEY(model_name, uuid, id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_prompt_entities`;
CREATE TABLE `ai_prompt_entities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_prompt_id` bigint NOT NULL,
  `ai_nlp_request_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `entity_type` varchar(50) NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY(ai_nlp_request_id) references ai_nlp_requests(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_prompt_id) references ai_prompts(id) ON DELETE RESTRICT,
  KEY(name, ai_prompt_id, entity_type)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_prompt_mentions`;
CREATE TABLE `ai_prompt_mentions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_prompt_entity_id` bigint NOT NULL,
  `mention_text` text NOT NULL,
  `mention_offset` int NULL,
  `mention_type` varchar(50) NOT NULL,
  `probability` decimal(10,9) NULL default 0.0,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY(ai_prompt_entity_id) references ai_prompt_entities(id) ON DELETE RESTRICT,
  KEY(probability, ai_prompt_entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_prompt_sentiments`;
CREATE TABLE `ai_prompt_sentiments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_prompt_id` bigint NOT NULL,
  `ai_nlp_request_id` bigint NOT NULL,
  `sentiment_text` text NULL, -- we allow null for the full prompt (no need to repeat that here!)
  `sentiment_offset` int NULL,
  `magnitude` decimal(10,9) NULL default 0.0,
  `score` decimal(10,9) NULL default 0.0,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY(ai_nlp_request_id) references ai_nlp_requests(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_prompt_id) references ai_prompts(id) ON DELETE RESTRICT,
  KEY(score, magnitude, ai_prompt_id),
  KEY(magnitude, score, ai_prompt_id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_prompt_classifiers`;
CREATE TABLE `ai_prompt_classifiers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_prompt_id` bigint NOT NULL,
  `ai_nlp_request_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `confidence` decimal(10,9) NULL default 0.0,
  `category_type` varchar(10) NOT NULL DEFAULT 'CLASSIFY', -- CLASSIFY | MODERATE
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY(ai_nlp_request_id) references ai_nlp_requests(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_prompt_id) references ai_prompts(id) ON DELETE RESTRICT,
  KEY(confidence, name, ai_prompt_id),
  KEY(category_type, confidence, name, ai_prompt_id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;


-- ============================
-- Images
-- ============================
DROP TABLE if exists `ai_requests`;
DROP TABLE if exists `ai_image_requests`;
CREATE TABLE `ai_image_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` varchar(65) NOT NULL,
  `ai_prompt_id` bigint NOT NULL,
  `model_name` varchar(50) NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY(ai_prompt_id) references ai_prompts(id) ON DELETE RESTRICT,
  UNIQUE KEY (`uuid`),
  KEY(model_name, uuid, id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

-- 2024-06-03
DROP TABLE if exists ai_videos;
CREATE TABLE `ai_videos` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `video_url` text NOT NULL,
  `frame_rate` decimal(8,6) NULL,
  `frame_count` int NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_images`;
CREATE TABLE `ai_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_prompt_id` bigint NULL,
  `ai_image_request_id` bigint NULL,
  `ai_video_id` bigint NULL,
  `video_frame_number` int NULL,
  `is_new_video_scene` tinyint NOT NULL DEFAULT 0,
  `is_video_scene_snap` tinyint NOT NULL DEFAULT 0,
  `image_url` text NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY(ai_video_id, video_frame_number),
  FOREIGN KEY(ai_image_request_id) references ai_image_requests(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_video_id) references ai_videos(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_prompt_id) references ai_prompts(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

-- 2024-06-03
-- alter table ai_images add column `ai_video_id` bigint NULL;
-- alter table ai_images add column   `video_frame_number` int NULL;
-- alter table ai_images add column   `is_new_video_scene` tinyint NOT NULL DEFAULT 0;
-- alter table ai_images add column   `is_video_scene_snap` tinyint NOT NULL DEFAULT 0;
-- alter table ai_images add FOREIGN KEY(ai_video_id) references ai_videos(id) ON DELETE RESTRICT;
-- alter table ai_images add UNIQUE KEY(ai_video_id, video_frame_number);

DROP TABLE if exists ai_sounds;
CREATE TABLE `ai_sounds` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_video_id` bigint NULL,
  `sound_url` text NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY(ai_video_id) references ai_videos(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_models`;
CREATE TABLE `ai_models` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY(name)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_similarity_requests`;
CREATE TABLE `ai_similarity_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` varchar(65) NULL,
  `ai_image_id` bigint NOT NULL,
  `ai_image_before_id` bigint NOT NULL,
  `ai_model_id` bigint NOT NULL,
  `ai_prompt_id` bigint NULL,
  `score` DECIMAL(20,18) NOT NULL DEFAULT 0,
  `explanation` VARCHAR(255) NULL,
  `is_scene_change` TINYINT NOT NULL DEFAULT 0,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY(uuid),
  FOREIGN KEY(ai_image_id) references ai_images(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_image_before_id) references ai_images(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_model_id) references ai_models(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_prompt_id) references ai_prompts(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_transcribe_requests`;
CREATE TABLE `ai_transcribe_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` varchar(65) NULL,
  `ai_sound_id` bigint NOT NULL,
  `ai_model_id` bigint NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY(uuid),
  FOREIGN KEY(ai_model_id) references ai_models(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_sound_id) references ai_sounds(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_transcriptions`;
CREATE TABLE `ai_transcriptions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_sound_id` bigint NOT NULL,
  `ai_transcribe_request_id` bigint NOT NULL,
  `transcription_text` text NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY(ai_transcribe_request_id),
  FOREIGN KEY(ai_transcribe_request_id) references ai_transcribe_requests(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_sound_id) references ai_sounds(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_sound_summary_requests`;
CREATE TABLE `ai_sound_summary_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` varchar(65) NOT NULL,
  `ai_transcription_id` bigint NOT NULL,
  `ai_model_id` bigint NOT NULL,
  `ai_prompt_id` bigint NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY(uuid),
  FOREIGN KEY(ai_model_id) references ai_models(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_transcription_id) references ai_transcriptions(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_prompt_id) references ai_prompts(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_sound_summaries`;
CREATE TABLE `ai_sound_summaries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_sound_id` bigint NOT NULL,
  `ai_sound_summary_request_id` bigint NOT NULL,
  `summary_text` text NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY(ai_sound_summary_request_id),
  FOREIGN KEY(ai_sound_summary_request_id) references ai_sound_summary_requests(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_sound_id) references ai_sounds(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_video_summary_requests`;
CREATE TABLE `ai_video_summary_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` varchar(65) NULL,
  `ai_video_id` bigint NOT NULL,
  `ai_model_id` bigint NOT NULL,
  `ai_prompt_id` bigint NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY(uuid),
  FOREIGN KEY(ai_model_id) references ai_models(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_video_id) references ai_videos(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_prompt_id) references ai_prompts(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_video_summaries`;
CREATE TABLE `ai_video_summaries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_video_id` bigint NOT NULL,
  `ai_video_summary_request_id` bigint NOT NULL,
  `summary_text` text NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY(ai_video_summary_request_id),
  FOREIGN KEY(ai_video_summary_request_id) references ai_video_summary_requests(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_video_id) references ai_videos(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

-- scenes of a video used to generate the summary
DROP TABLE if exists `ai_video_summary_scenes`;
CREATE TABLE `ai_video_summary_scenes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_video_id` bigint NOT NULL,
  `ai_video_summary_request_id` bigint NOT NULL,
  `ai_image_id` bigint NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY(ai_video_summary_request_id, ai_video_id, ai_image_id),
  FOREIGN KEY(ai_video_summary_request_id) references ai_video_summary_requests(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_video_id) references ai_videos(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_image_id) references ai_images(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_revised_prompts`;
CREATE TABLE `ai_revised_prompts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_image_request_id` bigint NOT NULL,
  `ai_prompt_id` bigint NOT NULL,
  `prompt` text NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY(ai_image_request_id) references ai_image_requests(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_prompt_id) references ai_prompts(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

-- ============================
-- image labels
-- ============================
-- label an image using a model
DROP TABLE if exists `ai_label_requests`;
CREATE TABLE `ai_label_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` varchar(65) NOT NULL,
  `ai_image_id` bigint NOT NULL,
  `ai_prompt_id` bigint NULL,
  `model_name` varchar(50) NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY(ai_image_id) references ai_images(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_prompt_id) references ai_prompts(id) ON DELETE RESTRICT,
  UNIQUE KEY (`uuid`),
  KEY(model_name, uuid, id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_image_labels`;
CREATE TABLE `ai_image_labels` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_label_request_id` bigint NOT NULL,
  `ai_image_id` bigint NOT NULL,
  `model_name` varchar(50) NOT NULL,
  `label` text NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY(ai_label_request_id) references ai_label_requests(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_image_id) references ai_images(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_image_label_annotations`;
CREATE TABLE `ai_image_label_annotations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_label_request_id` bigint NOT NULL,
  `ai_image_id` bigint NOT NULL,
  `model_name` varchar(50) NOT NULL,
  `label` varchar(100) NOT NULL,
  `mid` varchar(50) NULL,
  `score` decimal(10,8) NULL default 0.0,
  `topicality` decimal(10,8) NULL default 0.0,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY(ai_label_request_id) references ai_label_requests(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_image_id) references ai_images(id) ON DELETE RESTRICT,
  KEY(mid, score, topicality, label)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `meta_categories`;
CREATE TABLE `meta_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_image_label_meta_categories`;
CREATE TABLE `ai_image_label_meta_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_image_id` bigint NOT NULL,
  `ai_image_label_id` bigint NOT NULL,
  `meta_category_id` bigint NOT NULL,
  `meta_key` varchar(100) NOT NULL,
  `meta_value` text NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY(meta_category_id) references meta_categories(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_image_id) references ai_images(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_image_label_id) references ai_image_labels(id) ON DELETE RESTRICT,
  KEY(meta_key, meta_category_id, ai_image_id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_image_label_meta_objects`;
CREATE TABLE `ai_image_label_meta_objects` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_image_id` bigint NOT NULL,
  `ai_image_label_id` bigint NOT NULL,
  `object` varchar(255) NULL,
  `object_type` varchar(50) NULL,
  `object_value` TEXT NULL,
  `location` varchar(200) NULL,
  `relative_size` varchar(50) NULL,
  `color` varchar(255) NULL,
  `background_color` varchar(50) NULL,
  `brand` varchar(255) NULL,
  `gender` varchar(15) NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY(location, object_type, ai_image_id, ai_image_label_id),
  KEY(brand, ai_image_id, ai_image_label_id),
  KEY(gender, ai_image_id, ai_image_label_id),
  KEY(object_type, object, ai_image_id, ai_image_label_id),
  FOREIGN KEY(ai_image_id) references ai_images(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_image_label_id) references ai_image_labels(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_image_label_meta_shadows`;
CREATE TABLE `ai_image_label_meta_shadows` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_image_id` bigint NOT NULL,
  `ai_image_label_id` bigint NOT NULL,
  `subject` varchar(200) NOT NULL,
  `description` text NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY(subject, ai_image_id, ai_image_label_id),
  FOREIGN KEY(ai_image_id) references ai_images(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_image_label_id) references ai_image_labels(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_image_label_meta_color_gradients`;
CREATE TABLE `ai_image_label_meta_color_gradients` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_image_id` bigint NOT NULL,
  `ai_image_label_id` bigint NOT NULL,
  `location` varchar(200) NOT NULL,
  `description` text NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY(location, ai_image_id, ai_image_label_id),
  FOREIGN KEY(ai_image_id) references ai_images(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_image_label_id) references ai_image_labels(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

-- 20240703: embedding requests
DROP TABLE if exists `ai_embedding_requests`;
CREATE TABLE `ai_embedding_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` varchar(65) NOT NULL,
  `ai_image_id` bigint NOT NULL,
  `meta_category_id` bigint NULL,
  `model_name` varchar(50) NOT NULL,
  `dimension_count` int default 0,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY(meta_category_id) references meta_categories(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_image_id) references ai_images(id) ON DELETE RESTRICT,
  UNIQUE KEY (`uuid`),
  KEY(model_name, uuid, id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

DROP TABLE if exists `ai_upsert_requests`;
CREATE TABLE `ai_upsert_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` varchar(65) NOT NULL,
  `ai_image_id` bigint NOT NULL,
  `meta_category_id` bigint NULL,
  `model_name` varchar(50) NOT NULL,
  `dimension_count` int default 0,
  `upsert_count` int default 0,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY(meta_category_id) references meta_categories(id) ON DELETE RESTRICT,
  FOREIGN KEY(ai_image_id) references ai_images(id) ON DELETE RESTRICT,
  UNIQUE KEY (`uuid`),
  KEY(model_name, uuid, id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;

-- 20240710: add the concept of brands
CREATE TABLE `ai_brand_types` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;
insert into ai_brand_types values(null, 'Personal', 1, now(), now());
insert into ai_brand_types values(null, 'Product', 1, now(), now());
insert into ai_brand_types values(null, 'Service', 1, now(), now());
insert into ai_brand_types values(null, 'Retail', 1, now(), now());
insert into ai_brand_types values(null, 'Cultural or Geographic', 1, now(), now());
insert into ai_brand_types values(null, 'Corporate', 1, now(), now());
insert into ai_brand_types values(null, 'Online', 1, now(), now());
insert into ai_brand_types values(null, 'Offline', 1, now(), now());
insert into ai_brand_types values(null, 'Performance', 1, now(), now());
insert into ai_brand_types values(null, 'Luxury', 1, now(), now());
insert into ai_brand_types values(null, 'Lifestyle', 1, now(), now());
insert into ai_brand_types values(null, 'Experience', 1, now(), now());
insert into ai_brand_types values(null, 'Event', 1, now(), now());
insert into ai_brand_types values(null, 'Media', 1, now(), now());
insert into ai_brand_types values(null, 'Consumer Electronics', 1, now(), now());

CREATE TABLE `ai_brands` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `brand_type_id` bigint NULL,
  `status` tinyint default 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY (`name`),
  FOREIGN KEY(brand_type_id) references ai_brand_types(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB3 ;
insert into ai_brands values(null, 'Aceable', null, 1, now(), now());
insert into ai_brands values(null, 'Apple', (select id from ai_brand_types where name='Consumer Electronics'), 1, now(), now());

-- videos and images can be part of a brand
alter table ai_videos add column ai_brand_id bigint NULL after frame_count;
alter table ai_videos add foreign key (ai_brand_id) references ai_brands(id) ON DELETE RESTRICT;
alter table ai_images add column ai_brand_id bigint NULL after is_video_scene_snap;
alter table ai_images add foreign key (ai_brand_id) references ai_brands(id) ON DELETE RESTRICT;
-- end: 20240710


-- =======================================
-- reset schema
-- =======================================
DROP TABLE if exists `ai_image_label_meta_color_gradients`;
DROP TABLE if exists `ai_image_label_meta_shadows`;
DROP TABLE if exists `ai_image_label_meta_objects`;
DROP TABLE if exists `ai_image_label_meta_categories`;
DROP TABLE if exists `ai_image_label_annotations`;
DROP TABLE if exists `ai_image_labels`;
DROP TABLE if exists `ai_label_requests`;
