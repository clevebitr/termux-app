package com.termux.shell.input;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 键位表加载器。
 *
 * <p>目的：把「内置默认键位」「磁盘上的用户键位表」「写入默认模板」三种来源集中在一处，
 * 使窗口宿主只需取用解析完成的 {@link KeymapTable}。
 *
 * <p>行为：
 * <ul>
 *     <li>文件不存在时写出带说明的默认模板，并返回内置默认表；</li>
 *     <li>文件存在时以文件内容为准，内置默认表不参与合并，删除某行即等于取消该绑定；</li>
 *     <li>文件中全部行均无法解析时回退到内置默认表，避免用户误改导致快捷键整体失效。</li>
 * </ul>
 *
 * <p>约束：{@link #load(File)} 会访问磁盘，须在初始化阶段或后台线程调用。
 */
public final class KeymapLoader {

    /**
     * 内置默认键位。顺序即模板文件中的顺序，分组通过注释行体现。
     */
    private static final List<String> DEFAULT_LINES = Collections.unmodifiableList(Arrays.asList(
        "# 标签页",
        "ctrl+shift+t = " + ShellAction.NEW_TAB,
        "ctrl+shift+w = " + ShellAction.CLOSE_TAB,
        "ctrl+tab = " + ShellAction.NEXT_TAB,
        "ctrl+shift+tab = " + ShellAction.PREVIOUS_TAB,
        "alt+shift+left = " + ShellAction.MOVE_TAB_LEFT,
        "alt+shift+right = " + ShellAction.MOVE_TAB_RIGHT,
        "",
        "# 窗格",
        "alt+shift+d = " + ShellAction.SPLIT_RIGHT,
        "alt+shift+e = " + ShellAction.SPLIT_DOWN,
        "alt+shift+w = " + ShellAction.CLOSE_PANE,
        "alt+right = " + ShellAction.FOCUS_NEXT_PANE,
        "alt+left = " + ShellAction.FOCUS_PREVIOUS_PANE,
        "",
        "# 窗口与界面",
        "ctrl+shift+n = " + ShellAction.NEW_WINDOW,
        "ctrl+shift+p = " + ShellAction.COMMAND_PALETTE,
        "ctrl+shift+b = " + ShellAction.TOGGLE_TAB_BAR));

    private static final String HEADER =
        "# Termux Shell 键位表\n"
            + "# 语法：修饰键+按键 = 动作，修饰键取 ctrl、alt、shift、meta，可组合。\n"
            + "# 以 # 开头的行与空行被忽略；删除某行即取消该绑定。\n"
            + "# 关闭 shell.properties 中的 keymap-enabled 后本文件整体失效。\n";

    private KeymapLoader() {
    }

    /**
     * 加载键位表。文件缺失、读取失败或解析结果为空时返回内置默认表。
     */
    @NonNull
    public static KeymapTable load(@Nullable File file) {
        if (file == null) {
            return KeymapTable.parse(DEFAULT_LINES);
        }
        if (!file.isFile()) {
            writeDefaultTemplate(file);
            return KeymapTable.parse(DEFAULT_LINES);
        }
        List<String> lines = readLines(file);
        if (lines == null) {
            return KeymapTable.parse(DEFAULT_LINES);
        }
        KeymapTable table = KeymapTable.parse(lines);
        return table.isEmpty() ? KeymapTable.parse(DEFAULT_LINES) : table;
    }

    /**
     * 返回内置默认键位文本行，供调试与单元测试使用。
     */
    @NonNull
    public static List<String> getDefaultLines() {
        return DEFAULT_LINES;
    }

    @Nullable
    private static List<String> readLines(@NonNull File file) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeDefaultTemplate(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            for (String line : DEFAULT_LINES) {
                writer.write(line);
                writer.write('\n');
            }
        } catch (IOException e) {
            // 写入失败时按内置默认表运行；下次启动会重试写入。
        }
    }
}
