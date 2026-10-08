-- Structured landlord publication fields used by the publication workspace and later legal evidence snapshots.
ALTER TABLE `house`
    ADD COLUMN `area_sqm` decimal(8, 2) NOT NULL DEFAULT 0.00 COMMENT 'Usable area in square meters' AFTER `monthly_rent`,
    ADD COLUMN `rent_type` varchar(16) NOT NULL DEFAULT 'whole' COMMENT 'whole or shared' AFTER `area_sqm`,
    ADD COLUMN `bedroom_count` tinyint NOT NULL DEFAULT 1 AFTER `rent_type`,
    ADD COLUMN `living_room_count` tinyint NOT NULL DEFAULT 1 AFTER `bedroom_count`,
    ADD COLUMN `bathroom_count` tinyint NOT NULL DEFAULT 1 AFTER `living_room_count`,
    ADD COLUMN `deposit_months` tinyint NOT NULL DEFAULT 1 AFTER `bathroom_count`,
    ADD COLUMN `payment_cycle_months` tinyint NOT NULL DEFAULT 1 AFTER `deposit_months`,
    ADD COLUMN `property_fee` decimal(10, 2) NOT NULL DEFAULT 0.00 AFTER `payment_cycle_months`,
    ADD COLUMN `available_from` date DEFAULT NULL AFTER `property_fee`,
    ADD COLUMN `description` varchar(2000) DEFAULT NULL AFTER `available_from`,
    ADD COLUMN `rental_requirements` varchar(1000) DEFAULT NULL AFTER `description`,
    ADD COLUMN `facilities_json` text DEFAULT NULL AFTER `rental_requirements`,
    ADD COLUMN `cover_image_url` varchar(1024) DEFAULT NULL AFTER `facilities_json`;

ALTER TABLE `house_publish`
    ADD COLUMN `submitted_at` datetime DEFAULT NULL AFTER `deleted`,
    ADD COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER `submitted_at`,
    ADD COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `created_at`;
