CREATE TABLE IF NOT EXISTS `legal_contract`
(
    `id`                bigint       NOT NULL,
    `tenant_id`         bigint       NOT NULL,
    `conversation_id`   bigint       NOT NULL,
    `object_key`        varchar(512) NOT NULL,
    `original_filename` varchar(255) NOT NULL,
    `sha256`            char(64)     NOT NULL,
    `extracted_text`    longtext     NOT NULL,
    `status`            tinyint      NOT NULL DEFAULT 1,
    `created_at`        datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`        datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_legal_contract_content` (`tenant_id`, `conversation_id`, `sha256`),
    KEY `idx_legal_contract_conversation` (`conversation_id`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Tenant-private rental contract metadata and extracted text';

CREATE TABLE IF NOT EXISTS `legal_assessment_job`
(
    `id`            bigint       NOT NULL,
    `request_id`    varchar(64)  NOT NULL,
    `contract_id`   bigint       NOT NULL,
    `tenant_id`     bigint       NOT NULL,
    `status`        varchar(16)  NOT NULL,
    `summary`       varchar(1000)         DEFAULT NULL,
    `error_message` varchar(500)          DEFAULT NULL,
    `created_at`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `completed_at`  datetime(3)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_legal_job_request` (`tenant_id`, `request_id`),
    KEY `idx_legal_job_contract` (`contract_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Idempotent legal assessment jobs';

CREATE TABLE IF NOT EXISTS `legal_risk_item`
(
    `id`             bigint        NOT NULL,
    `job_id`         bigint        NOT NULL,
    `risk_code`      varchar(64)   NOT NULL,
    `risk_level`     varchar(16)   NOT NULL,
    `title`          varchar(255)  NOT NULL,
    `conclusion`     varchar(1000) NOT NULL,
    `evidence_quote` varchar(2000)          DEFAULT NULL,
    `legal_basis`    varchar(500)  NOT NULL,
    `suggestion`     varchar(1000) NOT NULL,
    `confidence`     int           NOT NULL,
    `ai_enhanced`    tinyint(1)    NOT NULL DEFAULT 0,
    `created_at`     datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_legal_risk_job_code` (`job_id`, `risk_code`),
    KEY `idx_legal_risk_job` (`job_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Structured legal risk findings';

CREATE TABLE IF NOT EXISTS `legal_evidence_ref`
(
    `id`            bigint        NOT NULL,
    `risk_item_id`  bigint        NOT NULL,
    `evidence_type` varchar(16)   NOT NULL COMMENT 'CONTRACT, CHAT, LAW',
    `source_id`     varchar(128)           DEFAULT NULL,
    `page_number`   int                    DEFAULT NULL,
    `clause_number` varchar(64)            DEFAULT NULL,
    `quote_text`    varchar(2000)          DEFAULT NULL,
    `source_url`    varchar(1000)          DEFAULT NULL,
    `created_at`    datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_legal_evidence_risk` (`risk_item_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Traceable contract, chat and law evidence';
