delimiter //
create procedure backfill_repo_replica()
begin
    declare done int default false;
    declare v_repo_id bigint;
    declare v_replica_json json;
    declare v_health_json json;
    declare v_lag_json json;
    declare v_lag_commits_json json;
    declare i int;
    declare len int;
    declare node_id varchar(64);

    declare cur cursor for
        select repo_id, replica_node_ids, replica_health, replica_lag_ms, replica_lag_commits
        from repo_location
        where replica_node_ids is not null and json_length(replica_node_ids) > 0;
    declare continue handler for not found set done = true;

    open cur;
    read_loop: loop
        fetch cur into v_repo_id, v_replica_json, v_health_json, v_lag_json, v_lag_commits_json;
        if done then leave read_loop; end if;

        set i = 0;
        set len = json_length(v_replica_json);
        while i < len do
            set node_id = json_unquote(json_extract(v_replica_json, concat('$[', i, ']')));

            insert into repo_replica (repo_id, node_id, health, lag_ms, lag_commits, updated_at)
            values (
                v_repo_id,
                node_id,
                json_unquote(json_extract(v_health_json, concat('$.\"', node_id, '\"'))),
                json_extract(v_lag_json, concat('$.\"', node_id, '\"')),
                json_extract(v_lag_commits_json, concat('$.\"', node_id, '\"')),
                now()
            )
            on duplicate key update
                health = values(health),
                lag_ms = values(lag_ms),
                lag_commits = values(lag_commits),
                updated_at = values(updated_at);

            set i = i + 1;
        end while;
    end loop;
    close cur;
end//
delimiter ;

call backfill_repo_replica();
drop procedure backfill_repo_replica;
