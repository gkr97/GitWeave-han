CREATE TABLE `user` (
                        `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK, 회원 식별자',
                        `email` VARCHAR(255) NOT NULL COMMENT '이메일(유니크)',
                        `password_hash` VARCHAR(255) NOT NULL COMMENT '패스워드 해시',
                        `name` VARCHAR(100) NULL COMMENT '이름/닉네임',
                        `profile_image_url` VARCHAR(500) NULL COMMENT '프로필 이미지',
                        `bio` TEXT NULL COMMENT '소개글',
                        `email_verified` BOOLEAN NOT NULL DEFAULT 0 CHECK (`email_verified` IN (0,1)) COMMENT '이메일 인증 여부',
                        `provider_code_id` BIGINT NULL COMMENT '소셜 로그인 제공자 코드',
                        `provider_id` VARCHAR(100) NULL COMMENT '소셜 제공자별 유저ID',
                        `timezone` VARCHAR(50) NULL COMMENT '타임존',
                        `location` VARCHAR(100) NULL COMMENT '지역',
                        `website_url` VARCHAR(255) NULL COMMENT '웹사이트',
                        `last_login_at` DATETIME NULL COMMENT '마지막 로그인',
                        `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                        `updated_at` DATETIME NULL COMMENT '수정 시각',
                        `is_active` BOOLEAN NOT NULL DEFAULT 1 CHECK (`is_active` IN (0,1)) COMMENT '활성화 여부',
                        `is_deleted` BOOLEAN NOT NULL DEFAULT 0 CHECK (`is_deleted` IN (0,1)) COMMENT '탈퇴/삭제 여부',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_user_email` (`email`),
                        UNIQUE KEY `uk_user_name` (`name`)
) COMMENT='서비스 회원 마스터';

CREATE TABLE `login_history` (
                                 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '로그 식별자',
                                 `user_id` BIGINT NOT NULL COMMENT '유저 PK',
                                 `ip_address` VARCHAR(50) NOT NULL COMMENT '로그인 IP',
                                 `user_agent` TEXT NULL COMMENT 'User Agent',
                                 `login_at` DATETIME NOT NULL COMMENT '로그인 시각',
                                 `success` BOOLEAN NOT NULL DEFAULT 1 CHECK (`success` IN (0,1)) COMMENT '로그인 성공 여부',
                                 PRIMARY KEY (`id`),
                                 KEY `idx_login_history_user_id` (`user_id`),
                                 CONSTRAINT `fk_login_history_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) COMMENT='로그인 이력';

CREATE TABLE `repository` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '저장소 PK',
                              `owner_id` BIGINT NOT NULL COMMENT '소유자(유저) PK',
                              `name` VARCHAR(100) NOT NULL COMMENT '저장소명',
                              `description` TEXT NULL COMMENT '설명',
                              `visibility_code_id` BIGINT NULL COMMENT '공개여부 코드',
                              `default_branch` VARCHAR(100) NOT NULL DEFAULT 'main' COMMENT '기본 브랜치명',
                              `license` VARCHAR(100) NULL COMMENT '라이선스',
                              `language` VARCHAR(50) NULL COMMENT '주요 언어',
                              `homepage_url` VARCHAR(255) NULL COMMENT '홈페이지',
                              `topics` TEXT NULL COMMENT '토픽',
                              `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                              `updated_at` DATETIME NULL COMMENT '수정 시각',
                              `is_deleted` BOOLEAN NOT NULL DEFAULT 0 CHECK (`is_deleted` IN (0,1)) COMMENT '삭제 여부',
                              PRIMARY KEY (`id`),
                              KEY `idx_repository_owner_id` (`owner_id`)
) COMMENT='저장소 메타정보';

CREATE TABLE `branch` (
                          `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '브랜치 PK',
                          `repository_id` BIGINT NOT NULL COMMENT '저장소 PK',
                          `creator_id` BIGINT NULL COMMENT '브랜치 생성자(유저) PK',
                          `name` VARCHAR(100) NOT NULL COMMENT '브랜치명',
                          `head_commit_hash` VARCHAR(40) NULL COMMENT 'HEAD 커밋 해시',
                          `last_commit_at` DATETIME NULL COMMENT '마지막 커밋 시각',
                          `is_protected` BOOLEAN NOT NULL DEFAULT 0 CHECK (`is_protected` IN (0,1)) COMMENT '보호여부',
                          `protection_rule` TEXT NULL COMMENT '브랜치 보호 규칙',
                          `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                          `is_default` BOOLEAN NOT NULL DEFAULT 0 CHECK (`is_default` IN (0,1)) COMMENT '기본 브랜치 여부',
                          PRIMARY KEY (`id`),
                          KEY `idx_branch_repository_id` (`repository_id`),
                          KEY `idx_branch_creator_id` (`creator_id`),
                          KEY `idx_branch_last_commit_at` (`last_commit_at`),
                          CONSTRAINT `fk_branch_repository_id` FOREIGN KEY (`repository_id`) REFERENCES `repository` (`id`),
                          CONSTRAINT `fk_branch_creator_id` FOREIGN KEY (`creator_id`) REFERENCES `user` (`id`)
) COMMENT='브랜치';

CREATE TABLE `repository_bookmark` (
                                       `user_id` BIGINT NOT NULL COMMENT '유저 PK',
                                       `repository_id` BIGINT NOT NULL COMMENT '저장소 PK',
                                       `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                                       PRIMARY KEY (`user_id`, `repository_id`),
                                       CONSTRAINT `fk_repository_bookmark_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
                                       CONSTRAINT `fk_repository_bookmark_repository_id` FOREIGN KEY (`repository_id`) REFERENCES `repository` (`id`)
) COMMENT='저장소 즐겨찾기';

CREATE TABLE `repository_stats` (
                                    `repository_id` BIGINT NOT NULL COMMENT '저장소 PK',
                                    `stars` INT NOT NULL DEFAULT 0 COMMENT '별 개수',
                                    `forks` INT NOT NULL DEFAULT 0 COMMENT '포크 수',
                                    `watchers` INT NOT NULL DEFAULT 0 COMMENT '와처 수',
                                    `issues` INT NOT NULL DEFAULT 0 COMMENT '이슈 수',
                                    `pull_requests` INT NOT NULL DEFAULT 0 COMMENT 'PR 수',
                                    `last_commit_at` DATETIME NULL COMMENT '최종 커밋 시각',
                                    PRIMARY KEY (`repository_id`),
                                    CONSTRAINT `fk_repository_stats_repository_id` FOREIGN KEY (`repository_id`) REFERENCES `repository` (`id`)
) COMMENT='저장소 통계';

CREATE TABLE `collaborator` (
                                `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
                                `repository_id` BIGINT NOT NULL COMMENT '저장소 PK',
                                `user_id` BIGINT NOT NULL COMMENT '유저 PK',
                                `role_code_id` BIGINT NOT NULL COMMENT '역할 코드',
                                `invited_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '초대 시각',
                                `accepted` BOOLEAN NOT NULL DEFAULT 0 CHECK (`accepted` IN (0,1)) COMMENT '수락 여부',
                                PRIMARY KEY (`id`),
                                KEY `idx_collaborator_repository_id` (`repository_id`),
                                KEY `idx_collaborator_user_id` (`user_id`),
                                CONSTRAINT `fk_collaborator_repository_id` FOREIGN KEY (`repository_id`) REFERENCES `repository` (`id`),
                                CONSTRAINT `fk_collaborator_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) COMMENT='저장소 콜라보레이터(멤버)';

CREATE TABLE `common_code` (
                               `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
                               `code` VARCHAR(50) NOT NULL COMMENT '코드 그룹 식별자',
                               `name` VARCHAR(100) NOT NULL COMMENT '코드명',
                               `description` TEXT NULL COMMENT '설명',
                               `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_common_code_code` (`code`)
) COMMENT='공통코드 그룹';

CREATE TABLE `common_code_detail` (
                                      `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
                                      `code_group_id` BIGINT NOT NULL COMMENT '공통코드 그룹 PK',
                                      `code` VARCHAR(50) NOT NULL COMMENT '상세 코드',
                                      `name` VARCHAR(100) NOT NULL COMMENT '코드명',
                                      `sort_order` INT NOT NULL DEFAULT 0 COMMENT '정렬순서',
                                      `is_active` BOOLEAN NOT NULL DEFAULT 1 CHECK (`is_active` IN (0,1)) COMMENT '활성여부',
                                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                                      PRIMARY KEY (`id`),
                                      KEY `idx_common_code_detail_group_id` (`code_group_id`),
                                      CONSTRAINT `fk_common_code_detail_group_id` FOREIGN KEY (`code_group_id`) REFERENCES `common_code` (`id`)
) COMMENT='공통코드 상세';

CREATE TABLE `email_verification` (
                                      `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
                                      `user_id` BIGINT NOT NULL COMMENT '유저 PK',
                                      `token` VARCHAR(255) NOT NULL COMMENT '인증 토큰',
                                      `is_used` BOOLEAN NOT NULL DEFAULT 0 CHECK (`is_used` IN (0,1)) COMMENT '사용 여부',
                                      `expires_at` DATETIME NOT NULL COMMENT '만료 시각',
                                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                                      PRIMARY KEY (`id`),
                                      KEY `idx_email_verification_user_id` (`user_id`),
                                      CONSTRAINT `fk_email_verification_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) COMMENT='이메일 인증';

CREATE TABLE `pull_request` (
                                `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PR PK',
                                `repository_id` BIGINT NOT NULL COMMENT '저장소 PK',
                                `author_id` BIGINT NOT NULL COMMENT '작성자(유저) PK',
                                `title` VARCHAR(255) NOT NULL COMMENT '제목',
                                `description` TEXT NULL COMMENT '설명',
                                `status_code_id` BIGINT NOT NULL COMMENT '상태 코드',
                                `merge_type_code_id` BIGINT NULL COMMENT '머지 타입',
                                `merged_by_id` BIGINT NULL COMMENT '병합자 PK',
                                `merged_at` DATETIME NULL COMMENT '병합 시각',
                                `closed_at` DATETIME NULL COMMENT '종료 시각',
                                `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                                `updated_at` DATETIME NULL COMMENT '수정 시각',
                                `target_branch` VARCHAR(100) NOT NULL COMMENT '타깃 브랜치',
                                `source_branch` VARCHAR(100) NOT NULL COMMENT '소스 브랜치',
                                `base_commit_hash` VARCHAR(40) NULL COMMENT 'Base 커밋 해시',
                                `head_commit_hash` VARCHAR(40) NULL COMMENT 'Head 커밋 해시',
                                PRIMARY KEY (`id`),
                                KEY `idx_pull_request_repo_id` (`repository_id`),
                                KEY `idx_pull_request_author_id` (`author_id`),
                                CONSTRAINT `fk_pull_request_repository_id` FOREIGN KEY (`repository_id`) REFERENCES `repository` (`id`),
                                CONSTRAINT `fk_pull_request_author_id` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`)
) COMMENT='풀리퀘스트';

CREATE TABLE `pull_request_file` (
                                     `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
                                     `pull_request_id` BIGINT NOT NULL COMMENT 'PR PK',
                                     `path` TEXT NOT NULL COMMENT '파일 경로',
                                     `status_code_id` BIGINT NOT NULL COMMENT '상태 코드',
                                     `additions` INT NOT NULL DEFAULT 0 COMMENT '추가 라인',
                                     `deletions` INT NOT NULL DEFAULT 0 COMMENT '삭제 라인',
                                     PRIMARY KEY (`id`),
                                     KEY `idx_pr_file_pull_request_id` (`pull_request_id`),
                                     CONSTRAINT `fk_pr_file_pull_request_id` FOREIGN KEY (`pull_request_id`) REFERENCES `pull_request` (`id`)
) COMMENT='PR 파일 변경';

CREATE TABLE `pull_request_commit` (
                                       `pull_request_id` BIGINT NOT NULL COMMENT 'PR PK',
                                       `commit_hash` VARCHAR(40) NOT NULL COMMENT '커밋 해시(DynamoDB 참조)',
                                       PRIMARY KEY (`pull_request_id`, `commit_hash`),
                                       KEY `idx_pr_commit_pull_request_id` (`pull_request_id`)
) COMMENT='PR별 커밋 매핑';

CREATE TABLE `pull_request_merge_log` (
                                          `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
                                          `pull_request_id` BIGINT NOT NULL COMMENT 'PR PK',
                                          `merged_by_id` BIGINT NOT NULL COMMENT '병합자(유저) PK',
                                          `merge_commit_hash` VARCHAR(40) NOT NULL COMMENT '병합 커밋 해시',
                                          `merge_type_code_id` BIGINT NULL COMMENT '머지 타입',
                                          `merged_at` DATETIME NOT NULL COMMENT '병합 시각',
                                          PRIMARY KEY (`id`),
                                          KEY `idx_merge_log_pull_request_id` (`pull_request_id`),
                                          CONSTRAINT `fk_merge_log_pull_request_id` FOREIGN KEY (`pull_request_id`) REFERENCES `pull_request` (`id`)
) COMMENT='PR 머지 로그';

CREATE TABLE `pull_request_reviewer` (
                                         `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
                                         `pull_request_id` BIGINT NOT NULL COMMENT 'PR PK',
                                         `reviewer_id` BIGINT NOT NULL COMMENT '리뷰어(유저) PK',
                                         `status_code_id` BIGINT NOT NULL COMMENT '상태 코드',
                                         `reviewed_at` DATETIME NULL COMMENT '리뷰 시각',
                                         PRIMARY KEY (`id`),
                                         KEY `idx_reviewer_pull_request_id` (`pull_request_id`),
                                         KEY `idx_reviewer_reviewer_id` (`reviewer_id`),
                                         CONSTRAINT `fk_reviewer_pull_request_id` FOREIGN KEY (`pull_request_id`) REFERENCES `pull_request` (`id`),
                                         CONSTRAINT `fk_reviewer_reviewer_id` FOREIGN KEY (`reviewer_id`) REFERENCES `user` (`id`)
) COMMENT='PR 리뷰어';

CREATE TABLE `pull_request_comment` (
                                        `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
                                        `pull_request_id` BIGINT NOT NULL COMMENT 'PR PK',
                                        `author_id` BIGINT NOT NULL COMMENT '작성자(유저) PK',
                                        `content` TEXT NOT NULL COMMENT '내용',
                                        `file_path` TEXT NULL COMMENT '파일 경로',
                                        `line_number` INT NULL COMMENT '라인 번호',
                                        `comment_type` VARCHAR(20) NOT NULL DEFAULT 'general' COMMENT '코멘트 타입',
                                        `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작성 시각',
                                        PRIMARY KEY (`id`),
                                        KEY `idx_pr_comment_pull_request_id` (`pull_request_id`),
                                        CONSTRAINT `fk_pr_comment_pull_request_id` FOREIGN KEY (`pull_request_id`) REFERENCES `pull_request` (`id`)
) COMMENT='PR 코멘트';

CREATE TABLE `pull_request_file_diff` (
                                          `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
                                          `pull_request_id` BIGINT NOT NULL COMMENT 'PR PK',
                                          `file_path` TEXT NOT NULL COMMENT '파일 경로',
                                          `additions` INT NOT NULL DEFAULT 0 COMMENT '추가 라인',
                                          `deletions` INT NOT NULL DEFAULT 0 COMMENT '삭제 라인',
                                          `patch` TEXT NULL COMMENT 'diff patch',
                                          `status_code_id` BIGINT NOT NULL COMMENT '상태 코드',
                                          `is_binary` BOOLEAN NOT NULL DEFAULT 0 CHECK (`is_binary` IN (0,1)) COMMENT '바이너리 여부',
                                          PRIMARY KEY (`id`),
                                          KEY `idx_pr_file_diff_pull_request_id` (`pull_request_id`),
                                          CONSTRAINT `fk_pr_file_diff_pull_request_id` FOREIGN KEY (`pull_request_id`) REFERENCES `pull_request` (`id`)
) COMMENT='PR 파일 Diff';

CREATE TABLE `personal_access_token` (
                                         `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
                                         `user_id` BIGINT NOT NULL COMMENT '유저 PK',
                                         `token_hash` VARCHAR(64) NOT NULL COMMENT 'SHA-256 토큰 해시',
                                         `name` VARCHAR(100) NULL COMMENT '토큰 별칭(설명용)',
                                         `last_used_at` DATETIME NULL COMMENT '최근 사용 시각',
                                         `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
                                         `expires_at` DATETIME NULL COMMENT '만료 시각(Null=영구)',
                                         `is_active` BOOLEAN NOT NULL DEFAULT 1 COMMENT '활성 여부(비활성시 로그인불가)',
                                         PRIMARY KEY (`id`),
                                         KEY `idx_pat_user_id` (`user_id`),
                                         CONSTRAINT `fk_pat_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) COMMENT='개인 접근 토큰(PAT)';

CREATE TABLE `personal_access_token_usage` (
                                               `id` BIGINT NOT NULL AUTO_INCREMENT,
                                               `token_id` BIGINT NOT NULL,
                                               `used_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               `ip_address` VARCHAR(50) NULL,
                                               `user_agent` VARCHAR(255) NULL,
                                               `success` BOOLEAN NOT NULL DEFAULT 1,
                                               PRIMARY KEY (`id`),
                                               KEY `idx_pat_usage_token_id` (`token_id`),
                                               CONSTRAINT `fk_pat_usage_token_id` FOREIGN KEY (`token_id`) REFERENCES `personal_access_token` (`id`)
) COMMENT='PAT 사용 이력';

CREATE TABLE `user_rename_history` (
                                       `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
                                       `user_id` BIGINT NOT NULL COMMENT '유저 PK',
                                       `old_username` VARCHAR(100) NOT NULL COMMENT '변경 전 username',
                                       `new_username` VARCHAR(100) NOT NULL COMMENT '변경 후 username',
                                       `changed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '변경 시각',
                                       PRIMARY KEY (`id`),
                                       KEY `idx_user_rename_history_user_id` (`user_id`),
                                       KEY `idx_user_rename_history_old_username` (`old_username`),
                                       CONSTRAINT `fk_user_rename_history_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) COMMENT='username 변경 이력';