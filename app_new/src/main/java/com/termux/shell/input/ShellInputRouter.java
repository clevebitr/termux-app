package com.termux.shell.input;

import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

/**
 * 外壳输入路由默认实现。
 *
 * <p>目的：在按键进入终端键序编码之前，先判定外壳级快捷键；未命中的按键原样交回窗口宿主继续分发。
 *
 * <p>行为：
 * <ul>
 *     <li>仅处理 {@code ACTION_DOWN}；按键抬起不参与快捷键判定；</li>
 *     <li>键位表为空、快捷键被禁用或按键未绑定时返回 false，由窗口继续分发；</li>
 *     <li>命中绑定时调用 {@link Host#execute(String)}，无论该动作是否可执行都视为已消费，
 *     以免同一按键同时触发终端输入。</li>
 * </ul>
 *
 * <p>约束：拖放与指针事件的完整语义在里程碑 M14 实现，本实现仅返回未消费。
 */
public final class ShellInputRouter implements InputRouter {

    /**
     * 动作宿主。返回值表示动作是否实际生效，仅用于日志；路由结果由绑定是否命中决定。
     */
    public interface Host {
        boolean execute(@NonNull String action);
    }

    private KeymapTable mKeymap = KeymapTable.EMPTY;
    private boolean mShellShortcutsEnabled = true;
    private Host mHost;

    public void setKeymap(@NonNull KeymapTable keymap) {
        mKeymap = keymap;
    }

    /**
     * 设置外壳快捷键开关。关闭后全部按键交给终端处理，与旧模块的
     * {@code disable-hardware-keyboard-shortcuts} 语义一致。
     */
    public void setShellShortcutsEnabled(boolean enabled) {
        mShellShortcutsEnabled = enabled;
    }

    public void setHost(@NonNull Host host) {
        mHost = host;
    }

    @Override
    public boolean onKeyDown(@NonNull KeyEvent event) {
        if (!mShellShortcutsEnabled || mHost == null || mKeymap.isEmpty()) {
            return false;
        }
        String action = mKeymap.findAction(event.getKeyCode(), event.getMetaState());
        if (action == null) {
            return false;
        }
        mHost.execute(action);
        return true;
    }

    @Override
    public boolean onKeyUp(@NonNull KeyEvent event) {
        return false;
    }

    @Override
    public boolean onGenericMotion(@NonNull MotionEvent event) {
        return false;
    }

    @Override
    public boolean onDrop(@NonNull DragEvent event) {
        return false;
    }
}
