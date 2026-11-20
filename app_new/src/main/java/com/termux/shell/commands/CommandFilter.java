package com.termux.shell.commands;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * 命令面板的筛选规则。
 *
 * <p>目的：以不依赖 Android 运行时的纯函数实现筛选，便于单元测试覆盖全部匹配分支。
 *
 * <p>行为：
 * <ul>
 *     <li>查询按空白切分为多个词，每个词都必须命中某一候选文本；</li>
 *     <li>命中包含两种方式：候选文本包含该词（子串），或候选文本按顺序包含该词的字符（子序列），
 *     后者用于「nwt」匹配「新建标签页」这类缩写输入；</li>
 *     <li>空查询匹配全部命令。</li>
 * </ul>
 *
 * <p>约束：比较一律忽略大小写；候选文本为 null 时按空文本处理。
 */
public final class CommandFilter {

    private CommandFilter() {
    }

    /**
     * 判断查询是否命中任一候选文本。
     *
     * @param query      用户输入，可以为 null 或空。
     * @param candidates 候选文本，通常为标题与分类。
     */
    public static boolean matches(@Nullable String query, @NonNull String... candidates) {
        if (query == null) {
            return true;
        }
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        String[] tokens = trimmed.toLowerCase(Locale.ROOT).split("\\s+");
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (!matchesToken(token, candidates)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesToken(@NonNull String token, @NonNull String[] candidates) {
        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (isSubstring(token, candidate) || isSubsequence(token, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSubstring(@NonNull String token, @NonNull String candidate) {
        return candidate.toLowerCase(Locale.ROOT).contains(token);
    }

    private static boolean isSubsequence(@NonNull String token, @NonNull String candidate) {
        String text = candidate.toLowerCase(Locale.ROOT);
        int matched = 0;
        for (int i = 0; i < text.length() && matched < token.length(); i++) {
            if (text.charAt(i) == token.charAt(matched)) {
                matched++;
            }
        }
        return matched == token.length();
    }
}
