package com.example.gitserver.module.pullrequest.application.query.support

object UnifiedDiffParser {

    private val HUNK_HEADER = Regex("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*")

    /**
     * unified diff 문자열을 ParsedHunk 리스트로 파싱
     */
    fun parse(patch: String): List<ParsedHunk> {
        val lines = patch.split("\n")
        val hunks = mutableListOf<ParsedHunk>()
        var current: HunkBuilder? = null

        for (raw in lines) {
            val line = raw.removeSuffix("\r")

            val m = HUNK_HEADER.matchEntire(line)
            if (m != null) {
                current?.let { hunks += it.build() }
                val oldStart = m.groupValues[1].toInt()
                val newStart = m.groupValues[2].toInt()
                current = HunkBuilder(header = line, oldStart = oldStart, newStart = newStart)
                continue
            }

            if (current == null) continue

            when {
                line.startsWith("+") -> current.add(
                    type = '+',
                    oldLine = null,
                    newLine = current.nextNew(),
                    content = line.substring(1)
                )
                line.startsWith("-") -> current.add(
                    type = '-',
                    oldLine = current.nextOld(),
                    newLine = null,
                    content = line.substring(1)
                )
                else -> {
                    val hasPrefix = line.startsWith(" ")
                    current.add(
                        type = ' ',
                        oldLine = current.nextOld(),
                        newLine = current.nextNew(),
                        content = if (hasPrefix) line.substring(1) else line
                    )
                }
            }
        }

        current?.let { hunks += it.build() }
        return hunks
    }

    private class HunkBuilder(
        private val header: String,
        private val oldStart: Int,
        private val newStart: Int
    ) {
        private var oldLineNumber = oldStart
        private var newLineNumber = newStart
        private var position = 0
        private val lines = mutableListOf<ParsedLine>()

        fun nextOld(): Int = oldLineNumber++
        fun nextNew(): Int = newLineNumber++

        fun add(type: Char, oldLine: Int?, newLine: Int?, content: String) {
            lines += ParsedLine(
                type = type,
                oldLine = oldLine,
                newLine = newLine,
                content = content,
                position = position++  
            )
        }

        fun build(): ParsedHunk = ParsedHunk(
            header = header,
            oldStart = oldStart,
            newStart = newStart,
            lines = lines.toList()
        )
    }
}
