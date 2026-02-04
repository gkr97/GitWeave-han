ALTER TABLE `repo_location`
  ADD COLUMN `replica_lag_commits` JSON NULL COMMENT 'replica lag in commits';
