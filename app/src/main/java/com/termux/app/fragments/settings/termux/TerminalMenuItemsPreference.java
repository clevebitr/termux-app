package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.menu.TerminalMenuAction;
import com.termux.app.terminal.menu.TerminalMenuConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link OrderedItemsPreference} that edits the actions of the terminal context menu, which are
 * stored in the `terminal-menu-items` property, in the order that they are shown in.
 */
@Keep
public class TerminalMenuItemsPreference extends OrderedItemsPreference {

    public TerminalMenuItemsPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TerminalMenuItemsPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @NonNull
    @Override
    protected List<Item> getAvailableItems(@NonNull Context context) {
        List<Item> items = new ArrayList<>();
        for (TerminalMenuAction action : TerminalMenuAction.values()) {
            // The session is not known here, so the title without the pid is shown, which the action
            // itself falls back to for a null session.
            items.add(new Item(action.getId(), action.getTitle(context, null), action.getIcon()));
        }
        return items;
    }

    @NonNull
    @Override
    protected String getDefaultValue() {
        return TerminalMenuConfig.DEFAULT_MENU_ITEMS;
    }
}
