CREATE TABLE `git_replication_dlq` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `task_id` BIGINT NOT NULL COMMENT 'replication task id',
  `repo_id` BIGINT NOT NULL COMMENT 'repository id',
  `source_node_id` VARCHAR(64) NOT NULL COMMENT 'source node id',
  `target_node_id` VARCHAR(64) NOT NULL COMMENT 'target node id',
  `attempt` INT NOT NULL COMMENT 'attempt count',
  `last_error` TEXT NULL COMMENT 'last error',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_git_replication_dlq_task` (`task_id`),
  KEY `idx_git_replication_dlq_repo` (`repo_id`)
) COMMENT='Git replication dead letter queue';
