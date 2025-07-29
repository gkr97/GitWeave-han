package com.example.gitserver.module.repository.exception

import com.example.gitserver.common.exception.BusinessException
import java.nio.file.Path

class GitHookCopyFailedException(hookSrc: Path, hookDst: Path) : BusinessException(
    code = "GIT_HOOK_COPY_FAILED",
    message = "Git hook 파일 복사에 실패했습니다. (src: $hookSrc, dst: $hookDst)",
)