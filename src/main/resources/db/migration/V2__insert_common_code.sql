-- 공통코드 그룹
INSERT INTO common_code (id, code, name, description, created_at) VALUES
                                                                      (1, 'PROVIDER', '소셜로그인제공자', '소셜 로그인 제공자', NOW()),
                                                                      (2, 'VISIBILITY', '저장소공개여부', '저장소 공개여부', NOW()),
                                                                      (3, 'ROLE', '저장소역할', '저장소 콜라보레이터 역할', NOW()),
                                                                      (4, 'PR_STATUS', 'PR상태', '풀리퀘스트 상태', NOW()),
                                                                      (5, 'PR_MERGE_TYPE', 'PR머지타입', '풀리퀘스트 병합방식', NOW()),
                                                                      (6, 'USER_STATUS', '회원상태', '회원 상태', NOW()),
                                                                      (7, 'EMAIL_VERIF', '이메일인증상태', '이메일 인증상태', NOW()),
                                                                      (8, 'LOGIN_TYPE', '로그인방식', '로그인 인증방식', NOW()),
                                                                      (9, 'REPO_TOPIC', '저장소토픽', '저장소 주제 태그', NOW()),
                                                                      (10, 'LANG', '프로그래밍언어', '주요 언어', NOW()),
                                                                      (11, 'LICENSE', '라이선스', '오픈소스/비오픈소스', NOW()),
                                                                      (12, 'ISSUE_STATUS', '이슈상태', '이슈 상태', NOW()),
                                                                      (13, 'COMMENT_TYPE', '코멘트유형', '코멘트 유형', NOW()),
                                                                      (14, 'PR_REVIEW_STATUS', 'PR 리뷰 상태', 'PR 리뷰 상태', NOW()),
                                                                      (15, 'PR_FILE_STATUS', 'PR 파일 상태', 'PR 파일 변경 상태 매핑', NOW());

-- PROVIDER
INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (1, 1, 'local', '자체계정', 1, 1, NOW()),
                                                                                                      (2, 1, 'kakao', '카카오', 4, 1, NOW());

-- VISIBILITY
INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (10, 2, 'public', '공개', 1, 1, NOW()),
                                                                                                      (11, 2, 'private', '비공개', 2, 1, NOW());

-- ROLE
INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (20, 3, 'owner', '소유자', 1, 1, NOW()),
                                                                                                      (21, 3, 'maintainer', '메인테이너', 2, 1, NOW()),
                                                                                                      (22, 3, 'developer', '개발자', 3, 1, NOW()),
                                                                                                      (23, 3, 'reporter', '리포터', 4, 1, NOW()),
                                                                                                      (24, 3, 'guest', '게스트', 5, 1, NOW());

-- PR_STATUS
INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (30, 4, 'open', '오픈', 1, 1, NOW()),
                                                                                                      (31, 4, 'closed', '종료', 2, 1, NOW()),
                                                                                                      (32, 4, 'merged', '병합', 3, 1, NOW());

-- PR_MERGE_TYPE
INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (40, 5, 'merge_commit', '머지커밋', 1, 1, NOW()),
                                                                                                      (41, 5, 'squash', '스쿼시', 2, 1, NOW()),
                                                                                                      (42, 5, 'rebase', '리베이스', 3, 1, NOW());

-- USER_STATUS
INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (50, 6, 'active', '정상', 1, 1, NOW()),
                                                                                                      (51, 6, 'inactive', '비활성', 2, 1, NOW()),
                                                                                                      (52, 6, 'deleted', '탈퇴', 3, 1, NOW()),
                                                                                                      (53, 6, 'banned', '정지', 4, 1, NOW());

-- EMAIL_VERIF
INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (60, 7, 'waiting', '대기중', 1, 1, NOW()),
                                                                                                      (61, 7, 'verified', '완료', 2, 1, NOW()),
                                                                                                      (62, 7, 'expired', '만료', 3, 1, NOW());

-- LOGIN_TYPE
INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (70, 8, 'password', '패스워드', 1, 1, NOW()),
                                                                                                      (71, 8, 'oauth', '소셜/OAuth', 2, 1, NOW());

-- REPO_TOPIC
INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (80, 9, 'ai', '인공지능', 1, 1, NOW()),
                                                                                                      (81, 9, 'web', '웹', 2, 1, NOW()),
                                                                                                      (82, 9, 'backend', '백엔드', 3, 1, NOW()),
                                                                                                      (83, 9, 'frontend', '프론트엔드', 4, 1, NOW()),
                                                                                                      (84, 9, 'devops', '데브옵스', 5, 1, NOW()),
                                                                                                      (85, 9, 'etc', '기타', 6, 1, NOW());

-- LANG
INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (90, 10, 'python', 'Python', 1, 1, NOW()),
                                                                                                      (91, 10, 'java', 'Java', 2, 1, NOW()),
                                                                                                      (92, 10, 'javascript', 'JavaScript', 3, 1, NOW()),
                                                                                                      (93, 10, 'typescript', 'TypeScript', 4, 1, NOW()),
                                                                                                      (94, 10, 'go', 'Go', 5, 1, NOW()),
                                                                                                      (95, 10, 'csharp', 'C#', 6, 1, NOW()),
                                                                                                      (96, 10, 'cpp', 'C++', 7, 1, NOW()),
                                                                                                      (97, 10, 'ruby', 'Ruby', 8, 1, NOW()),
                                                                                                      (98, 10, 'php', 'PHP', 9, 1, NOW()),
                                                                                                      (99, 10, 'etc', '기타', 99, 1, NOW());

-- LICENSE
INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (100, 11, 'mit', 'MIT', 1, 1, NOW()),
                                                                                                      (101, 11, 'apache2', 'Apache-2.0', 2, 1, NOW()),
                                                                                                      (102, 11, 'gplv3', 'GPLv3', 3, 1, NOW()),
                                                                                                      (103, 11, 'mpl20', 'MPL-2.0', 4, 1, NOW()),
                                                                                                      (104, 11, 'bsd3', 'BSD 3-Clause', 5, 1, NOW()),
                                                                                                      (105, 11, 'unlicense', 'Unlicense', 6, 1, NOW()),
                                                                                                      (106, 11, 'proprietary', '전용', 7, 1, NOW());

-- ISSUE_STATUS
INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (110, 12, 'open', '오픈', 1, 1, NOW()),
                                                                                                      (111, 12, 'closed', '종료', 2, 1, NOW()),
                                                                                                      (112, 12, 'resolved', '해결', 3, 1, NOW());

-- COMMENT_TYPE
INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (120, 13, 'general', '일반코멘트', 1, 1, NOW()),
                                                                                                      (121, 13, 'review', '리뷰', 2, 1, NOW()),
                                                                                                      (122, 13, 'inline', '라인별코멘트', 3, 1, NOW()),
                                                                                                      (123, 13, 'issue', '이슈코멘트', 4, 1, NOW());

-- PR 리뷰 상태  상세
INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (130, 14, 'pending', '대기',   1, 1, NOW()),
                                                                                                      (131, 14, 'approved', '승인',  2, 1, NOW());

INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (132, 14, 'changes_requested', '변경요청', 3, 1, NOW()),
                                                                                                      (133, 14, 'dismissed', '무시됨', 4, 1, NOW());

INSERT INTO common_code_detail (id, code_group_id, code, name, sort_order, is_active, created_at) VALUES
                                                                                                      (140, 15, 'ADDED',    '추가',     1, 1, NOW()),
                                                                                                      (141, 15, 'MODIFIED', '수정',     2, 1, NOW()),
                                                                                                      (142, 15, 'DELETED',  '삭제',     3, 1, NOW()),
                                                                                                      (143, 15, 'RENAMED',  '이름변경', 4, 1, NOW()),
                                                                                                      (144, 15, 'COPIED',   '복사',     5, 1, NOW());