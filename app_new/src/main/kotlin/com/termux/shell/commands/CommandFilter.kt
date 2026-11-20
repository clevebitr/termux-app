package com.termux.shell.commands

import java.util.Locale

/**
 * 命令面板的筛选规则。
 *
 * 目的：以不依赖 Android 运行时的纯函数实现筛选，便于单元测试覆盖全部匹配分支。
 *
 * 行为：
 * - 查询按空白切分为多个词，每个词都必须命中某一候选文本；
 * - 命中包含两种方式：候选文本包含该词（子串），或候选文本按顺序包含该词的字符（子序列），
 *   后者用于「nt」匹配「New tab」这类缩写输入；
 * - 空查询匹配全部命令。
 *
 * 约束：比较一律忽略大小写；候选文本为 null 时按空文本处理。
 */
object CommandFilter {

    /**
     * 判断查询是否命中任一候选文本。
     *
     * @param query 用户输入，可以为 null 或空。
     * @param candidates 候选文本，通常为标题与分类。
     */
    fun matches(query: String?, vararg candidates: String?): Boolean {
        val trimmed = query?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return true
        }
        return trimmed.lowercase(Locale.ROOT)
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .all { token -> matchesToken(token, candidates) }
    }

    private fun matchesToken(token: String, candidates: Array<out String?>): Boolean =
        candidates.any { candidate ->
            candidate != null && (isSubstring(token, candidate) || isSubsequence(token, candidate))
        }

    private fun isSubstring(token: String, candidate: String): Boolean =
        candidate.lowercase(Locale.ROOT).contains(token)

    private fun isSubsequence(token: String, candidate: String): Boolean {
        val text = candidate.lowercase(Locale.ROOT)
        var matched = 0
        for (index in text.indices) {
            if (matched == token.length) {
                break
            }
            if (text[index] == token[matched]) {
                matched++
            }
        }
        return matched == token.length
    }
}
