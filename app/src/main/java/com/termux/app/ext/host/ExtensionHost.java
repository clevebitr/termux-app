package com.termux.app.ext.host;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.ext.ForkExtensions;
import com.termux.app.ext.api.Extension;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 目的 — 提供本进程内扩展状态与登记表的唯一读取入口。
 *
 * <p>行为 — 持有 {@link ForkExtensions#installed()} 的快照，每次读取时用
 * {@link ExtensionGraph} 解析一次规格（{@link ExtensionSpec}）与用户标志的组合，因此终端宿主
 * （{@link ExtensionManager}）、扩展设置页、设置搜索与各功能页闸门看到的状态一定一致。</p>
 *
 * <p>约束 — 只在主线程调用；进程内单例，因为扩展设置页与终端 Activity 是两个实例，必须共享同一份
 * 状态。解析在主线程同步完成，扩展数量为个位数，不缓存，以免用户改过标志后读到旧快照。</p>
 */
public final class ExtensionHost {

    @Nullable
    private static volatile ExtensionHost sInstance;

    @NonNull
    private final Context appContext;
    @NonNull
    private final List<Extension> installed;

    private ExtensionHost(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.installed = ForkExtensions.installed();
    }

    /** @return The process wide host, creating it on first use. */
    @NonNull
    public static ExtensionHost get(@NonNull Context context) {
        ExtensionHost host = sInstance;
        if (host == null) {
            synchronized (ExtensionHost.class) {
                host = sInstance;
                if (host == null) {
                    host = new ExtensionHost(context);
                    sInstance = host;
                }
            }
        }
        return host;
    }

    /** @return The registered extensions of this build, in registration order. */
    @NonNull
    public List<Extension> installed() {
        return installed;
    }

    /** @return The resolved state of every registered extension, dependencies first. */
    @NonNull
    public List<ExtensionStatus> statuses() {
        return ExtensionGraph.resolve(ExtensionPreferences.specs(installed),
            ExtensionPreferences.storedFlags(appContext, installed));
    }

    /**
     * @return The state of {@code id}, or {@code null} if the extension is not installed. A caller that gates
     *         an entry point has to treat {@code null} as disabled.
     */
    @Nullable
    public ExtensionState stateOf(@NonNull String id) {
        for (ExtensionStatus status : statuses()) {
            if (status.id.equals(id)) return status.state;
        }
        return null;
    }

    /** @return Whether {@code id} is installed and may run; {@code false} for an unknown id. */
    public boolean isRunning(@NonNull String id) {
        ExtensionState state = stateOf(id);
        return state != null && state.isRunning();
    }

    /** @return The extension with {@code id}, or {@code null} if it is not installed. */
    @Nullable
    public Extension extension(@NonNull String id) {
        for (Extension extension : installed) {
            if (extension.id().equals(id)) return extension;
        }
        return null;
    }

    /** @return Whether the user may disable the extension; {@code false} for an unknown id. */
    public boolean isUserDisableable(@NonNull String id) {
        Extension extension = extension(id);
        return extension != null && extension.isUserDisableable();
    }

    /** @return The ids of the extensions that would also stop when {@code id} is disabled. */
    @NonNull
    public List<String> dependentsOf(@NonNull String id) {
        return ExtensionGraph.dependentsOf(ExtensionPreferences.specs(installed), id);
    }

    /** @return The ids of the installed extensions that may not run, for hiding their entries. */
    @NonNull
    public Set<String> disabledIds() {
        Set<String> disabled = new LinkedHashSet<>();
        for (ExtensionStatus status : statuses()) {
            if (!status.state.isRunning()) disabled.add(status.id);
        }
        return Collections.unmodifiableSet(disabled);
    }

    /**
     * Store the user's choice.
     *
     * @return Whether the choice was accepted; an extension that is not user disableable rejects disabling.
     */
    public boolean setEnabled(@NonNull String id, boolean enabled) {
        Extension extension = extension(id);
        if (extension == null) return false;
        return ExtensionPreferences.setEnabled(appContext, id, enabled, extension.isUserDisableable());
    }
}
