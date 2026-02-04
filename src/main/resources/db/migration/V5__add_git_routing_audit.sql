CREATE TABLE `git_routing_audit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `actor` VARCHAR(128) NULL COMMENT 'actor identity',
  `action` VARCHAR(64) NOT NULL COMMENT 'action type',
  `repo_id` BIGINT NULL COMMENT 'repository id',
  `node_id` VARCHAR(64) NULL COMMENT 'node id',
  `payload` TEXT NULL COMMENT 'request payload',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_git_routing_audit_repo` (`repo_id`),
  KEY `idx_git_routing_audit_node` (`node_id`)
) COMMENT='Git routing admin audit log';
