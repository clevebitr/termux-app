package com.termux.shell.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 会话宿主契约。
 *
 * <p>目的：把「终端界面」与「进程与伪终端」彻底分离。骨架阶段由同一进程内的实现承担；
 * 运行时拆分完成后，实现将位于独立进程，本契约即为两进程之间的通信边界
 * （详见 {@code docs/UPGRADE.md} 第 6 节方案 B）。
 *
 * <p>约束：
 * <ul>
 *     <li>全部方法必须可在任意线程调用，实现方负责线程切换与串行化；</li>
 *     <li>不得在回调中直接操作视图，观察者需自行切换到主线程；</li>
 *     <li>输出回调携带的字节数组由实现方复用，观察者必须在返回前完成拷贝。</li>
 * </ul>
 */
public interface SessionHost {

    /**
     * 创建会话。
     *
     * @return 新会话标识；创建失败时返回 null，失败原因通过日志输出。
     */
    @Nullable
    SessionId createSession(@NonNull SessionRequest request);

    /**
     * 向会话写入数据。
     *
     * @param data   数据缓冲区。
     * @param offset 起始偏移。
     * @param length 长度。
     */
    void write(@NonNull SessionId id, @NonNull byte[] data, int offset, int length);

    /**
     * 调整会话的终端尺寸。
     *
     * <p>像素尺寸用于支持以像素为单位定位的图形协议与鼠标上报，缺失时相关功能将退化为字符网格定位。
     */
    void resize(@NonNull SessionId id, int columns, int rows, int cellWidthPixels, int cellHeightPixels);

    /**
     * 关闭会话。实现方应保证重复调用安全。
     */
    void closeSession(@NonNull SessionId id);

    @Nullable
    SessionDescriptor describe(@NonNull SessionId id);

    @NonNull
    List<SessionDescriptor> describeAll();

    void addObserver(@NonNull Observer observer);

    void removeObserver(@NonNull Observer observer);

    /**
     * 会话创建参数。
     *
     * <p>约束：{@code argv} 至少包含一项；环境变量在运行时侧与默认环境合并，同名键覆盖默认值。
     */
    final class SessionRequest {

        private final String mWorkingDirectory;
        private final List<String> mArgv;
        private final Map<String, String> mEnvironment;

        public SessionRequest(@Nullable String workingDirectory, @NonNull List<String> argv,
                              @NonNull Map<String, String> environment) {
            if (argv.isEmpty()) {
                throw new IllegalArgumentException("argv 不得为空");
            }
            mWorkingDirectory = workingDirectory;
            mArgv = argv;
            mEnvironment = environment;
        }

        @Nullable
        public String getWorkingDirectory() {
            return mWorkingDirectory;
        }

        @NonNull
        public List<String> getArgv() {
            return mArgv;
        }

        @NonNull
        public Map<String, String> getEnvironment() {
            return mEnvironment;
        }
    }

    /**
     * 会话观察者。
     *
     * <p>约束：回调可能在任意线程触发，且不得抛出异常；抛出异常将中断实现方的通知循环。
     */
    interface Observer {

        void onOutput(@NonNull SessionId id, @NonNull byte[] data, int length);

        void onExit(@NonNull SessionId id, int exitCode);

        void onTitleChanged(@NonNull SessionId id, @Nullable String title);
    }
}
