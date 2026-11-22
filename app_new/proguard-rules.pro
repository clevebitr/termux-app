# 性能验证包使用的 R8 规则。
#
# 终端核心通过名称绑定调用原生方法（Java_com_termux_shell_terminal_JNI_*），
# R8 不得重命名该类的 native 方法，否则运行期会抛 UnsatisfiedLinkError。
-keep class com.termux.shell.terminal.JNI { *; }

# 终端视图与仿真器由外壳通过公开 API 使用，保留类名以便诊断与 JNI 名称绑定核对；
# 允许裁剪未被引用的成员，避免整体关闭优化。
-keepnames class com.termux.shell.terminal.**
-keepnames class com.termux.shell.termview.**

# org.json 由平台提供，压缩阶段无需额外规则；Compose 自带 consumer rules。
-dontwarn org.json.**
