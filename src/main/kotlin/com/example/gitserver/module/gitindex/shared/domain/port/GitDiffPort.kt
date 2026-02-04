package com.example.gitserver.module.gitindex.shared.domain.port

import com.example.gitserver.module.gitindex.shared.domain.vo.ChangedFile


interface GitDiffPort {

    /**
     * 두 커밋/브랜치 간의 변경된 파일 목록을 조회한다.
     *
     * @param bareGitPath bare 저장소 절대경로
     * @param baseRefOrSha 기준 커밋/브랜치
     * @param headRefOrSha 비교 커밋/브랜치
     * @return 변경된 파일 목록
     * @throws IllegalArgumentException 지정된 ref 또는 SHA-1이 유효하지 않은 경우
     */
    fun listChangedFiles(
        bareGitPath: String,
        baseRefOrSha: String,
        headRefOrSha: String
    ): List<ChangedFile>

    /**
     * 특정 파일에 대한 패치(diff) 내용을 생성한다.
     *
     * @param bareGitPath bare 저장소 절대경로
     * @param baseRefOrSha 기준 커밋/브랜치
     * @param headRefOrSha 비교 커밋/브랜��
     * @param path 변경된 파일 경로 (예: src/main/App.java)
     * @return 패치 내용 (바이트 배열)
     * @throws IllegalArgumentException 지정된 파일이 변경되지 않았거나 존재하지 않는 경우
     */
    fun renderPatch(
        bareGitPath: String,
        baseRefOrSha: String,
        headRefOrSha: String,
        path: String
    ): ByteArray

    /**
     * 주어진 ref 또는 SHA-1을 실제 커밋 해시로 변환한다.
     *
     * @param bareGitPath bare 저장소 절대경로
     * @param refOrSha 브랜치명, 태그명, 또는 SHA-1
     * @return 실제 커밋 해시 (40자)
     * @throws IllegalArgumentException refOrSha가 유효하지 않거나 커밋을 찾지 못한 경우
     */
    fun resolveCommitHash(
        bareGitPath: String,
        refOrSha: String
    ): String
}
