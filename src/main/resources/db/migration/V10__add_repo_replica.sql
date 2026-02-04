create table if not exists repo_replica (
    repo_id bigint not null,
    node_id varchar(64) not null,
    health varchar(32),
    lag_ms bigint,
    lag_commits bigint,
    updated_at datetime not null,
    primary key (repo_id, node_id)
);

create index idx_repo_replica_node on repo_replica (node_id);
