package com.termux.shell;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.termux.shell.config.PropertiesSettingsRepository;
import com.termux.shell.config.SettingsRepository;
import com.termux.shell.config.SettingsSpec;
import com.termux.shell.config.ShellPaths;
import com.termux.shell.input.KeymapLoader;
import com.termux.shell.input.KeymapTable;
import com.termux.shell.log.ShellLog;

import java.io.File;
import java.util.List;

/**
 * 新外壳的进程入口。
 *
 * <p>目的：集中装配进程级单例（路径、配置、键位表），避免各组件通过静态字段互相引用。
 *
 * <p>行为：启动时解析配置目录、读取属性文件与键位表，并按配置设置日夜模式。
 * 配置文件或键位表缺失时写入默认模板；读取失败时按内置默认值运行。
 *
 * <p>约束：
 * <ul>
 *     <li>此处只创建轻量对象；会话、终端视图等重对象由窗口宿主按需创建；</li>
 *     <li>启动阶段读取的两个文件均为数 KB 量级，允许在主线程完成，避免界面在首帧后跳变；
 *     若后续新增更大的配置文件，应改为后台加载并在加载完成后通知界面；</li>
 *     <li>重载配置会重新设置默认日夜模式，界面可能因此重建；窗格与标签结构通过实例状态恢复。</li>
 * </ul>
 */
public final class ShellApplication extends Application {

    private static final String SUB_TAG = "ShellApplication";

    private static ShellPaths sShellPaths;
    private static PropertiesSettingsRepository sSettingsRepository;
    private static KeymapTable sKeymap = KeymapTable.EMPTY;

    @Override
    public void onCreate() {
        super.onCreate();
        sShellPaths = ShellPaths.forApplication(this);
        if (!sShellPaths.ensureTermuxDir()) {
            ShellLog.warn(SUB_TAG, "配置目录创建失败：" + sShellPaths.getTermuxDir(), null);
        }
        sSettingsRepository = PropertiesSettingsRepository.open(sShellPaths.getSettingsFile());
        logInvalidSettings(sSettingsRepository);

        File keymapFile = sShellPaths.getKeymapFile();
        sKeymap = KeymapLoader.load(keymapFile);
        ShellLog.info(SUB_TAG, "配置就绪：配置项 " + sSettingsRepository.keys().size()
            + " 项，键位 " + sKeymap.size() + " 条，键位表=" + keymapFile.getAbsolutePath());

        applyTheme(sSettingsRepository);
    }

    /**
     * 返回路径解析器。启动完成后非空。
     */
    @NonNull
    public static ShellPaths paths() {
        if (sShellPaths == null) {
            throw new IllegalStateException("ShellPaths 尚未初始化，ShellApplication.onCreate 未执行");
        }
        return sShellPaths;
    }

    /**
     * 返回进程级配置来源。骨架阶段为属性文件实现。
     */
    @NonNull
    public static SettingsRepository settings() {
        if (sSettingsRepository == null) {
            throw new IllegalStateException("配置尚未初始化，ShellApplication.onCreate 未执行");
        }
        return sSettingsRepository;
    }

    /**
     * 返回当前生效的键位表。
     */
    @NonNull
    public static KeymapTable keymap() {
        return sKeymap;
    }

    /**
     * 重新读取配置文件与键位表。
     *
     * <p>说明：命令面板的「重新加载配置文件」调用本方法。读取失败时保留旧快照并返回 false。
     *
     * @return 是否成功读取配置文件。
     */
    public static boolean reloadConfig() {
        if (sSettingsRepository == null || sShellPaths == null) {
            return false;
        }
        boolean reloaded = sSettingsRepository.reload();
        if (!reloaded) {
            return false;
        }
        logInvalidSettings(sSettingsRepository);
        sKeymap = KeymapLoader.load(sShellPaths.getKeymapFile());
        applyTheme(sSettingsRepository);
        ShellLog.info(SUB_TAG, "配置已重新加载，键位 " + sKeymap.size() + " 条");
        return true;
    }

    private static void logInvalidSettings(@NonNull PropertiesSettingsRepository repository) {
        List<String> invalidKeys = repository.getInvalidKeys();
        if (!invalidKeys.isEmpty()) {
            ShellLog.warn(SUB_TAG, "以下配置项的取值非法，已回退到默认值：" + invalidKeys, null);
        }
    }

    private static void applyTheme(@NonNull SettingsRepository settings) {
        String theme = settings.getString(SettingsSpec.KEY_APP_THEME, SettingsSpec.THEME_SYSTEM);
        int mode;
        if (SettingsSpec.THEME_DARK.equals(theme)) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        } else if (SettingsSpec.THEME_LIGHT.equals(theme)) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
