ALTER TABLE `legal_contract`
    ADD COLUMN `document_type` varchar(16) NOT NULL DEFAULT 'TEXT' AFTER `extracted_text`,
    ADD COLUMN `page_count` int DEFAULT NULL AFTER `document_type`,
    ADD COLUMN `locator_precision` varchar(32) NOT NULL DEFAULT 'UNPAGED_TEXT' AFTER `page_count`;

ALTER TABLE `legal_assessment_job`
    ADD COLUMN `region_code` varchar(32) NOT NULL DEFAULT 'GLOBAL' AFTER `tenant_id`,
    ADD COLUMN `as_of_date` date NOT NULL AFTER `region_code`;

ALTER TABLE `legal_evidence_ref`
    ADD COLUMN `paragraph_index` int DEFAULT NULL AFTER `page_number`,
    ADD COLUMN `locator_precision` varchar(32) DEFAULT NULL AFTER `clause_number`;

CREATE TABLE IF NOT EXISTS `legal_document_block`
(
    `id`                bigint       NOT NULL,
    `contract_id`       bigint       NOT NULL,
    `block_index`       int          NOT NULL,
    `page_number`       int                   DEFAULT NULL,
    `paragraph_index`   int                   DEFAULT NULL,
    `locator_precision` varchar(32)  NOT NULL,
    `heading`           varchar(255)          DEFAULT NULL,
    `block_text`        mediumtext   NOT NULL,
    `char_start`        int          NOT NULL,
    `char_end`          int          NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_legal_block_contract_index` (`contract_id`, `block_index`),
    KEY `idx_legal_block_contract_page` (`contract_id`, `page_number`, `paragraph_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Parsed contract blocks with stable evidence locators';

CREATE TABLE IF NOT EXISTS `legal_source`
(
    `id`             bigint        NOT NULL,
    `title`          varchar(255)  NOT NULL,
    `issuer`         varchar(255)  NOT NULL,
    `region_code`    varchar(32)   NOT NULL,
    `effective_from` date          NOT NULL,
    `effective_to`   date                   DEFAULT NULL,
    `source_url`     varchar(1000) NOT NULL,
    `content_hash`   char(64)      NOT NULL,
    `status`         tinyint       NOT NULL DEFAULT 1,
    `created_at`     datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`     datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_legal_source_hash` (`content_hash`),
    KEY `idx_legal_source_scope` (`region_code`, `status`, `effective_from`, `effective_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Versioned authoritative legal sources';

CREATE TABLE IF NOT EXISTS `legal_source_chunk`
(
    `id`             bigint      NOT NULL,
    `source_id`      bigint      NOT NULL,
    `chunk_index`    int         NOT NULL,
    `article_number` varchar(64)          DEFAULT NULL,
    `chunk_text`     mediumtext  NOT NULL,
    `embedding_json` json                 DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_legal_chunk_source_index` (`source_id`, `chunk_index`),
    KEY `idx_legal_chunk_source` (`source_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Article-aware law chunks with optional embeddings';
