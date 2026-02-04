CREATE TABLE `git_replication_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `repo_id` BIGINT NOT NULL COMMENT 'repository id',
  `source_node_id` VARCHAR(64) NOT NULL COMMENT 'primary node id',
  `target_node_id` VARCHAR(64) NOT NULL COMMENT 'replica node id',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|RUNNING|SUCCEEDED|FAILED',
  `attempt` INT NOT NULL DEFAULT 0 COMMENT 'attempt count',
  `last_error` TEXT NULL COMMENT 'last error',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NULL COMMENT 'updated time',
  PRIMARY KEY (`id`),
  KEY `idx_git_replication_repo` (`repo_id`),
  KEY `idx_git_replication_status` (`status`)
) COMMENT='Git replication task queue';
