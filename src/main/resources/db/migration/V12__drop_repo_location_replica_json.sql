alter table repo_location
    drop column replica_node_ids,
    drop column replica_health,
    drop column replica_lag_ms,
    drop column replica_lag_commits;
