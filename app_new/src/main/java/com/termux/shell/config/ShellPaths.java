package com.termux.shell.config;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * 外壳的路径解析。
 *
 * <p>目的：把「配置写在哪里」集中到单一类型，避免各包自行拼接路径。用户配置写入应用私有目录下的
 * 独立主目录，布局等内部状态写入应用私有目录的另一子目录，二者的生命周期与卸载清理一致。
 *
 * <p>布局：{@code filesDir/home/.termux/shell.properties} 为用户可编辑的配置文件，
 * {@code filesDir/home/.termux/keymap.properties} 为键位表；布局状态在后续里程碑写入
 * {@code filesDir/layout/}（见 {@code docs/DELIVERY_PLAN.md} 第 2 节 D3）。
 *
 * <p>约束：本类型只做路径计算与目录创建，不读写文件内容；路径解析不依赖 Android 运行时，
 * 因此可在单元测试中以临时目录构造。
 */
public final class ShellPaths {

    /** 应用私有主目录相对于 {@code filesDir} 的名称。包名切换后该目录即用户主目录。 */
    public static final String HOME_DIR_NAME = "home";

    /** 用户配置目录名，与旧应用的 {@code ~/.termux} 习惯保持一致。 */
    public static final String TERMUX_DIR_NAME = ".termux";

    public static final String SETTINGS_FILE_NAME = "shell.properties";
    public static final String KEYMAP_FILE_NAME = "keymap.properties";

    private final File mAppDir;
    private final File mHomeDir;
    private final File mTermuxDir;

    /**
     * @param appFilesDir 应用私有目录，通常取 {@code Context.getFilesDir()}。
     */
    public ShellPaths(@NonNull File appFilesDir) {
        mAppDir = appFilesDir;
        mHomeDir = new File(appFilesDir, HOME_DIR_NAME);
        mTermuxDir = new File(mHomeDir, TERMUX_DIR_NAME);
    }

    /**
     * 按应用上下文创建路径解析器。
     */
    @NonNull
    public static ShellPaths forApplication(@NonNull Context context) {
        return new ShellPaths(context.getFilesDir());
    }

    @NonNull
    public File getAppDir() {
        return mAppDir;
    }

    /**
     * 返回应用主目录。骨架阶段位于应用私有目录内，包名切换后指向真实用户主目录。
     */
    @NonNull
    public File getHomeDir() {
        return mHomeDir;
    }

    @NonNull
    public File getTermuxDir() {
        return mTermuxDir;
    }

    @NonNull
    public File getSettingsFile() {
        return new File(mTermuxDir, SETTINGS_FILE_NAME);
    }

    @NonNull
    public File getKeymapFile() {
        return new File(mTermuxDir, KEYMAP_FILE_NAME);
    }

    /**
     * 创建配置目录。
     *
     * @return 目录是否可用。创建失败时返回 false，调用方应记录日志并回退到内置默认值。
     */
    public boolean ensureTermuxDir() {
        return mTermuxDir.isDirectory() || mTermuxDir.mkdirs();
    }
}
