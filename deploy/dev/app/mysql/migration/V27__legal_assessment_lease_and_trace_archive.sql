ALTER TABLE `legal_assessment_job`
    ADD COLUMN `attempt_count` int NOT NULL DEFAULT 0 AFTER `status`,
    ADD COLUMN `lease_owner` varchar(64) DEFAULT NULL AFTER `attempt_count`,
    ADD COLUMN `lease_until` datetime(3) DEFAULT NULL AFTER `lease_owner`,
    ADD COLUMN `next_run_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER `lease_until`,
    ADD KEY `idx_legal_job_dispatch` (`status`, `next_run_at`, `lease_until`, `id`);

CREATE TABLE IF NOT EXISTS `legal_trace_event_archive`
(
    `id`              bigint       NOT NULL,
    `job_id`          bigint       NOT NULL,
    `stage`           varchar(64)  NOT NULL,
    `status`          varchar(16)  NOT NULL,
    `started_at`      datetime(3)  NOT NULL,
    `ended_at`        datetime(3)  DEFAULT NULL,
    `duration_millis` bigint        DEFAULT NULL,
    `details_json`    json         DEFAULT NULL,
    `created_at`      datetime(3)  NOT NULL,
    `archived_at`     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_legal_trace_archive_job` (`job_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Archived legal assessment trace events';
