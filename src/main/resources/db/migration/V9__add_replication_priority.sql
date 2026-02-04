ALTER TABLE `git_replication_task`
  ADD COLUMN `priority` INT NOT NULL DEFAULT 0 COMMENT 'higher is more urgent';
