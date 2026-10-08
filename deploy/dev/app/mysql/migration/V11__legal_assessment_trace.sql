CREATE TABLE IF NOT EXISTS `legal_trace_event`
(
    `id`              bigint       NOT NULL,
    `job_id`          bigint       NOT NULL,
    `stage`           varchar(64)  NOT NULL,
    `status`          varchar(16)  NOT NULL COMMENT 'RUNNING, SUCCESS, FAILED',
    `started_at`      datetime(3)  NOT NULL,
    `ended_at`        datetime(3)           DEFAULT NULL,
    `duration_millis` bigint                DEFAULT NULL,
    `details_json`    json                  DEFAULT NULL,
    `created_at`      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_legal_trace_job_stage` (`job_id`, `stage`, `started_at`),
    KEY `idx_legal_trace_job_status` (`job_id`, `status`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only legal assessment runtime trace events';
