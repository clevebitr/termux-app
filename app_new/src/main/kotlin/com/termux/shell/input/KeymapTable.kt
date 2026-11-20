package com.termux.shell.input

import android.view.KeyEvent
import java.util.Locale

/**
 * 外壳级键位表。
 *
 * 目的：把外壳快捷键从代码中分离为可配置数据，使同一份外壳可适配不同键盘布局与用户习惯，
 * 并使快捷键与终端键序编码互不干扰。
 *
 * 语法：每行一条绑定，格式为 `修饰键+按键 = 动作`，例如：
 * ```
 * # 标签页
 * ctrl+shift+t = new-tab
 * alt+left     = focus-previous-pane
 * ```
 * 以 `#` 开头的行与空行被忽略；修饰键取值为 `ctrl`、`alt`、`shift`、`meta`，可组合；
 * 按键取值为单个字母、单个数字，或 `tab`、`enter`、`escape`、`space`、`left`、`right`、
 * `up`、`down`、`home`、`end`、`pageup`、`pagedown`、`backspace`、`delete`。
 *
 * 约束：
 * - 解析为纯计算过程，不依赖 Android 运行时，可在 JVM 单元测试中直接验证；
 * - 非法行被跳过而非抛出异常；
 * - 同一按键组合重复出现时，首条绑定生效，后续绑定被忽略；
 * - 动作名称本身不做校验，动作是否被界面实现由 [ShellAction] 判定。
 */
class KeymapTable private constructor(private val bindings: Map<Int, Binding>) {

    /**
     * 查找按键组合对应的动作。
     *
     * @param keyCode 平台键码。
     * @param metaState 修饰键状态，可包含左右变体位；实现内部只保留通用修饰位。
     * @return 动作名称；未绑定时返回 null。
     */
    fun findAction(keyCode: Int, metaState: Int): String? =
        bindings[bindingKey(keyCode, normalizeModifiers(metaState))]?.action

    /**
     * 返回绑定到指定动作的快捷键展示文本，例如 `Ctrl+Shift+T`。
     *
     * 同一动作绑定多个组合时返回按解析顺序的第一条；未绑定时返回 null。
     */
    fun findShortcut(action: String): String? =
        bindings.values.firstOrNull { it.action == action }?.display

    val size: Int
        get() = bindings.size

    val isEmpty: Boolean
        get() = bindings.isEmpty()

    private data class Binding(
        val keyCode: Int,
        val modifiers: Int,
        val action: String,
        val display: String,
    )

    companion object {

        /** 空表。用于外壳快捷键被整体禁用的场景。 */
        val EMPTY: KeymapTable = KeymapTable(emptyMap())

        private const val MODIFIER_NONE = 0

        /** 内部修饰位。使用自有位定义而非平台掩码，避免平台掩码的低位落在同一位置导致组合冲突。 */
        private const val MODIFIER_CTRL = 1
        private const val MODIFIER_ALT = 1 shl 1
        private const val MODIFIER_SHIFT = 1 shl 2
        private const val MODIFIER_META = 1 shl 3

        /**
         * 解析键位表文本行。
         *
         * @param lines 按文件顺序排列的文本行，可以为 null。
         * @return 解析结果；无法识别的行被跳过。
         */
        fun parse(lines: List<String>?): KeymapTable {
            if (lines.isNullOrEmpty()) {
                return EMPTY
            }
            val bindings = HashMap<Int, Binding>()
            for (line in lines) {
                val parsed = parseLine(line) ?: continue
                val key = bindingKey(parsed.keyCode, parsed.modifiers)
                bindings.putIfAbsent(key, parsed)
            }
            return KeymapTable(bindings)
        }

        /** 便于单元测试与调试的字符串入口。 */
        fun parse(text: String?): KeymapTable {
            if (text.isNullOrEmpty()) {
                return EMPTY
            }
            return parse(text.split('\n'))
        }

        private fun parseLine(rawLine: String?): Binding? {
            val line = rawLine?.trim() ?: return null
            if (line.isEmpty() || line.startsWith("#")) {
                return null
            }
            val separator = line.indexOf('=')
            if (separator <= 0 || separator == line.length - 1) {
                return null
            }
            val accelerator = line.substring(0, separator).trim()
            val action = line.substring(separator + 1).trim()
            if (accelerator.isEmpty() || action.isEmpty()) {
                return null
            }

            val tokens = accelerator.split('+')
            var modifiers = MODIFIER_NONE
            for (index in 0 until tokens.size - 1) {
                val modifier = modifierOf(tokens[index].trim())
                if (modifier == MODIFIER_NONE) {
                    return null
                }
                modifiers = modifiers or modifier
            }

            val keyToken = tokens.last().trim()
            val keyCode = keyCodeOf(keyToken)
            if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                return null
            }
            return Binding(keyCode, modifiers, action, describe(modifierNames(modifiers), keyDisplayName(keyToken)))
        }

        private fun modifierOf(token: String): Int = when (token.lowercase(Locale.ROOT)) {
            "ctrl", "control" -> MODIFIER_CTRL
            "alt" -> MODIFIER_ALT
            "shift" -> MODIFIER_SHIFT
            "meta", "win", "super" -> MODIFIER_META
            else -> MODIFIER_NONE
        }

        private fun keyCodeOf(token: String): Int {
            val name = token.lowercase(Locale.ROOT)
            if (name.length == 1) {
                val c = name[0]
                if (c in 'a'..'z') {
                    return KeyEvent.KEYCODE_A + (c - 'a')
                }
                if (c in '0'..'9') {
                    return KeyEvent.KEYCODE_0 + (c - '0')
                }
                return KeyEvent.KEYCODE_UNKNOWN
            }
            return when (name) {
                "tab" -> KeyEvent.KEYCODE_TAB
                "enter", "return" -> KeyEvent.KEYCODE_ENTER
                "escape", "esc" -> KeyEvent.KEYCODE_ESCAPE
                "space" -> KeyEvent.KEYCODE_SPACE
                "left" -> KeyEvent.KEYCODE_DPAD_LEFT
                "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
                "up" -> KeyEvent.KEYCODE_DPAD_UP
                "down" -> KeyEvent.KEYCODE_DPAD_DOWN
                "home" -> KeyEvent.KEYCODE_MOVE_HOME
                "end" -> KeyEvent.KEYCODE_MOVE_END
                "pageup", "pgup" -> KeyEvent.KEYCODE_PAGE_UP
                "pagedown", "pgdn" -> KeyEvent.KEYCODE_PAGE_DOWN
                "backspace" -> KeyEvent.KEYCODE_DEL
                "delete", "del" -> KeyEvent.KEYCODE_FORWARD_DEL
                else -> KeyEvent.KEYCODE_UNKNOWN
            }
        }

        private fun modifierNames(modifiers: Int): List<String> = buildList {
            if (modifiers and MODIFIER_CTRL != 0) add("Ctrl")
            if (modifiers and MODIFIER_ALT != 0) add("Alt")
            if (modifiers and MODIFIER_SHIFT != 0) add("Shift")
            if (modifiers and MODIFIER_META != 0) add("Meta")
        }

        private fun keyDisplayName(token: String): String {
            val name = token.trim()
            if (name.length == 1) {
                return name.uppercase(Locale.ROOT)
            }
            return when (name.lowercase(Locale.ROOT)) {
                "esc", "escape" -> "Esc"
                "pgup", "pageup" -> "PageUp"
                "pgdn", "pagedown" -> "PageDown"
                "return", "enter" -> "Enter"
                "space" -> "Space"
                "tab" -> "Tab"
                "left" -> "Left"
                "right" -> "Right"
                "up" -> "Up"
                "down" -> "Down"
                "home" -> "Home"
                "end" -> "End"
                "backspace" -> "Backspace"
                "del", "delete" -> "Delete"
                else -> name
            }
        }

        private fun describe(modifiers: List<String>, keyName: String): String =
            if (modifiers.isEmpty()) keyName else modifiers.joinToString("") { "$it+" } + keyName

        /**
         * 把平台修饰位归一化为内部修饰位。
         *
         * 说明：平台掩码包含左右变体（例如 `META_CTRL_LEFT_ON`），且部分通用位与变体位不重叠，
         * 因此必须按掩码判定，不能直接按通用位判定。
         */
        private fun normalizeModifiers(metaState: Int): Int {
            var normalized = MODIFIER_NONE
            if (metaState and KeyEvent.META_CTRL_MASK != 0) normalized = normalized or MODIFIER_CTRL
            if (metaState and KeyEvent.META_ALT_MASK != 0) normalized = normalized or MODIFIER_ALT
            if (metaState and KeyEvent.META_SHIFT_MASK != 0) normalized = normalized or MODIFIER_SHIFT
            if (metaState and KeyEvent.META_META_MASK != 0) normalized = normalized or MODIFIER_META
            return normalized
        }

        private fun bindingKey(keyCode: Int, modifiers: Int): Int = (keyCode shl 8) or (modifiers and 0x0F)
    }
}
