package com.termux.shell;

import android.app.Application;

import androidx.annotation.NonNull;

import com.termux.shell.config.MemorySettingsRepository;
import com.termux.shell.config.SettingsRepository;

/**
 * 新外壳的进程入口。
 *
 * <p>目的：集中装配进程级单例，避免各组件通过静态字段互相引用。
 * <p>约束：此处只允许创建无副作用的轻量对象；任何磁盘或网络操作必须延后至首次访问。
 */
public final class ShellApplication extends Application {

    private static SettingsRepository sSettingsRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        sSettingsRepository = new MemorySettingsRepository();
    }

    /**
     * 返回进程级配置来源。骨架阶段为内存实现，持久化实现在配置层里程碑中替换。
     */
    @NonNull
    public static SettingsRepository settings() {
        if (sSettingsRepository == null) {
            sSettingsRepository = new MemorySettingsRepository();
        }
        return sSettingsRepository;
    }
}
