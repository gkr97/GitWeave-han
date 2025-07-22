-- user
INSERT INTO user (id, email, password_hash, name, profile_image_url, bio, email_verified, provider_code_id, provider_id, timezone, location, website_url, last_login_at, created_at, updated_at, is_active, is_deleted) VALUES
                                                                                                                                                                                                                            (1, 'admin@example.com', '$2a$10$adminhash', '관리자', NULL, '최초 관리자 계정', 1, 1, NULL, 'Asia/Seoul', 'Seoul', NULL, NOW(), NOW(), NOW(), 1, 0),
                                                                                                                                                                                                                            (2, 'devuser@example.com', '$2a$10$devhash', '홍길동', NULL, '서비스 개발자', 1, 2, NULL, 'Asia/Seoul', 'Busan', NULL, NOW(), NOW(), NOW(), 1, 0),
                                                                                                                                                                                                                            (3, 'guest@example.com', '$2a$10$guesthash', '게스트', NULL, NULL, 0, NULL, NULL, 'Asia/Seoul', NULL, NULL, NULL, NOW(), NOW(), 1, 0);

-- repository
INSERT INTO repository (id, owner_id, name, description, visibility_code_id, default_branch, license, language, homepage_url, topics, created_at, updated_at, is_deleted) VALUES
                                                                                                                                                                              (1, 1, 'platform-core', '서비스 코어 저장소', 10, 'main', 'mit', 'python', NULL, 'backend,ai', NOW(), NOW(), 0),
                                                                                                                                                                              (2, 2, 'web-client', '웹 프론트엔드 저장소', 10, 'main', 'apache2', 'typescript', NULL, 'frontend,web', NOW(), NOW(), 0);

-- branch
INSERT INTO branch (id, repository_id, name, head_commit_hash, is_protected, protection_rule, created_at, is_default) VALUES
                                                                                                                          (1, 1, 'main', NULL, 0, NULL, NOW(), 1),
                                                                                                                          (2, 2, 'main', NULL, 0, NULL, NOW(), 1);

-- repository_stats
INSERT INTO repository_stats (repository_id, stars, forks, watchers, issues, pull_requests, last_commit_at) VALUES
                                                                                                                (1, 2, 1, 3, 1, 0, NOW()),
                                                                                                                (2, 0, 0, 0, 0, 0, NOW());

-- collaborator
INSERT INTO collaborator (id, repository_id, user_id, role_code_id, invited_at, accepted) VALUES
                                                                                              (1, 1, 1, 20, NOW(), 1),   -- 관리자, 소유자
                                                                                              (2, 1, 2, 21, NOW(), 1),   -- 홍길동, 메인테이너
                                                                                              (3, 2, 2, 20, NOW(), 1),   -- 홍길동, 소유자
                                                                                              (4, 2, 3, 24, NOW(), 0);   -- 게스트, 게스트

-- login_history
INSERT INTO login_history (id, user_id, ip_address, user_agent, login_at, success) VALUES
                                                                                       (1, 1, '127.0.0.1', 'Mozilla/5.0', NOW(), 1),
                                                                                       (2, 2, '127.0.0.2', 'Mozilla/5.0', NOW(), 1),
                                                                                       (3, 3, '127.0.0.3', 'Mozilla/5.0', NOW(), 0);

-- repository_bookmark
INSERT INTO repository_bookmark (user_id, repository_id, created_at) VALUES
                                                                         (1, 1, NOW()), (2, 2, NOW()), (3, 1, NOW());

-- email_verification
INSERT INTO email_verification (id, user_id, token, is_used, expires_at, created_at) VALUES
                                                                                         (1, 1, 'sample-token-1', 1, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW()),
                                                                                         (2, 2, 'sample-token-2', 0, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW()),
                                                                                         (3, 3, 'sample-token-3', 0, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW());

-- pull_request
INSERT INTO pull_request (id, repository_id, author_id, title, description, status_code_id, merge_type_code_id, merged_by_id, merged_at, closed_at, created_at, updated_at, target_branch, source_branch, base_commit_hash, head_commit_hash)
VALUES
    (1, 1, 2, '첫 PR', '테스트 PR입니다.', 30, 40, 1, NOW(), NULL, NOW(), NOW(), 'main', 'feature/test', NULL, NULL);

-- pull_request_file
INSERT INTO pull_request_file (id, pull_request_id, path, status_code_id, additions, deletions)
VALUES
    (1, 1, 'README.md', 31, 5, 0);

-- pull_request_commit
INSERT INTO pull_request_commit (pull_request_id, commit_hash) VALUES
    (1, 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');

-- pull_request_merge_log
INSERT INTO pull_request_merge_log (id, pull_request_id, merged_by_id, merge_commit_hash, merge_type_code_id, merged_at) VALUES
    (1, 1, 1, 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 40, NOW());

-- pull_request_reviewer
INSERT INTO pull_request_reviewer (id, pull_request_id, reviewer_id, status_code_id, reviewed_at) VALUES
    (1, 1, 1, 32, NOW());

-- pull_request_comment
INSERT INTO pull_request_comment (id, pull_request_id, author_id, content, file_path, line_number, comment_type, created_at) VALUES
    (1, 1, 2, 'LGTM', 'README.md', 1, 'general', NOW());

-- pull_request_file_diff
INSERT INTO pull_request_file_diff (id, pull_request_id, file_path, additions, deletions, patch, status_code_id, is_binary) VALUES
    (1, 1, 'README.md', 5, 0, '+추가', 31, 0);
