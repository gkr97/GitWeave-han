ALTER TABLE `pull_request`
    ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic Lock 버전' AFTER `head_commit_hash`;

ALTER TABLE `branch`
    ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic Lock 버전' AFTER `is_default`;

ALTER TABLE `pull_request_reviewer`
    ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic Lock 버전' AFTER `updated_at`;
