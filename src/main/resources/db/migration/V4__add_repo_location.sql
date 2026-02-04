CREATE TABLE `git_node` (
  `node_id` VARCHAR(64) NOT NULL COMMENT 'Git storage node id',
  `host` VARCHAR(255) NOT NULL COMMENT 'host:port or address',
  `zone` VARCHAR(64) NULL COMMENT 'availability zone',
  `region` VARCHAR(64) NULL COMMENT 'region',
  `status` VARCHAR(32) NOT NULL DEFAULT 'healthy' COMMENT 'healthy|degraded|down',
  `disk_usage_pct` DECIMAL(5,2) NULL COMMENT 'disk usage percent',
  `iops` INT NULL COMMENT 'recent iops',
  `repo_count` INT NULL COMMENT 'repo count on node',
  `last_heartbeat_at` DATETIME NULL COMMENT 'last heartbeat time',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NULL COMMENT 'updated time',
  PRIMARY KEY (`node_id`)
) COMMENT='Git storage nodes';

CREATE TABLE `repo_location` (
  `repo_id` BIGINT NOT NULL COMMENT 'repository id',
  `primary_node_id` VARCHAR(64) NOT NULL COMMENT 'primary node id',
  `replica_node_ids` JSON NULL COMMENT 'replica node id list',
  `replica_health` JSON NULL COMMENT 'replica health map',
  `replica_lag_ms` JSON NULL COMMENT 'replica lag(ms) map',
  `last_write_commit` VARCHAR(64) NULL COMMENT 'last write commit hash',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`repo_id`),
  KEY `idx_repo_location_primary` (`primary_node_id`)
) COMMENT='Repo to git storage node mapping';
