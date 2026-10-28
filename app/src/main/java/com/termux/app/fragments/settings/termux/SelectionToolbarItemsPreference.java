package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.menu.TerminalMenuConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link OrderedItemsPreference} that edits the actions of the floating text selection toolbar,
 * which are stored in the `selection-toolbar-items` property, in the order that they are shown in.
 *
 * The last action cannot be removed, since the selection toolbar would otherwise be empty and the
 * user could not copy the selected text anymore.
 */
@Keep
public class SelectionToolbarItemsPreference extends OrderedItemsPreference {

    public SelectionToolbarItemsPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SelectionToolbarItemsPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @NonNull
    @Override
    protected List<Item> getAvailableItems(@NonNull Context context) {
        List<Item> items = new ArrayList<>();
        items.add(new Item(TerminalMenuConfig.SELECTION_TOOLBAR_COPY, context.getString(com.termux.view.R.string.copy_text), "\u2398"));
        items.add(new Item(TerminalMenuConfig.SELECTION_TOOLBAR_PASTE, context.getString(com.termux.view.R.string.paste_text), "\u2398"));
        items.add(new Item(TerminalMenuConfig.SELECTION_TOOLBAR_MORE, context.getString(com.termux.view.R.string.text_selection_more), "\u2026"));
        return items;
    }

    @NonNull
    @Override
    protected String getDefaultValue() {
        return TerminalMenuConfig.DEFAULT_SELECTION_TOOLBAR_ITEMS;
    }

    @Override
    protected int getMinimumItemCount() {
        return 1;
    }
}
