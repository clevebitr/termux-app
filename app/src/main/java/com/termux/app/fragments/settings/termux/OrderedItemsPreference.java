package com.termux.app.fragments.settings.termux;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.shared.data.DataUtils;
import com.termux.shared.view.ViewUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Preference} that edits an ordered list of items, like the actions of the terminal context
 * menu or the buttons of the floating text selection toolbar, which is stored as a comma separated list
 * of ids in the `termux.properties` file.
 *
 * The order matters, since it is the order that the items are shown in, so a multi select list, which
 * only stores a set of ids, cannot be used. The dialog shows the items that are shown and the items
 * that are hidden in two sections, where clicking an item adds or removes it and the arrows change the
 * order of the shown items.
 */
@Keep
public abstract class OrderedItemsPreference extends Preference {

    /** An item that can be added to the list. */
    public static final class Item {
        /** The id that is stored in the properties file. */
        @NonNull
        public final String id;
        /** The label that is shown in the settings screen and in the dialog. */
        @NonNull
        public final CharSequence label;
        /** A single unicode character that is shown on the right edge of the item, or empty. */
        @NonNull
        public final String icon;

        public Item(@NonNull String id, @NonNull CharSequence label, @NonNull String icon) {
            this.id = id;
            this.label = label;
            this.icon = icon;
        }
    }

    private static final float ROW_PADDING_HORIZONTAL_DP = 8;
    private static final float ROW_PADDING_VERTICAL_DP = 6;
    private static final float BUTTON_MIN_SIZE_DP = 32;

    public OrderedItemsPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public OrderedItemsPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** @return The items that can be selected, in the order they are listed in the dialog. */
    @NonNull
    protected abstract List<Item> getAvailableItems(@NonNull Context context);

    /** @return The value of the preference if it is not set in the `termux.properties` file. */
    @NonNull
    protected abstract String getDefaultValue();

    /**
     * @return The minimum number of items that the list must have, so that the last item that cannot be
     *         removed, like the copy action of the selection toolbar, is not removed by accident.
     */
    protected int getMinimumItemCount() {
        return 0;
    }

    /**
     * Returns the summary of the preference, which is the list of the items that are shown, in the order
     * they are shown.
     *
     * The summary is computed here and not set with {@link #setSummary(CharSequence)} from
     * {@link #onBindViewHolder(PreferenceViewHolder)}, since {@code setSummary()} calls
     * {@code notifyChanged()}, which must not be called while the {@code RecyclerView} of the preferences
     * is computing a layout, otherwise it crashes with "Cannot call this method while RecyclerView is
     * computing a layout or scrolling". {@code Preference.onBindViewHolder()} reads the summary with this
     * method, so it does not have to be set at all.
     */
    @NonNull
    @Override
    public CharSequence getSummary() {
        return getSummaryFor(getSelectedIds());
    }

    @Override
    protected void onClick() {
        showItemsDialog();
    }

    /** @return The selected ids, in the order they are shown. */
    @NonNull
    private List<String> getSelectedIds() {
        // getPersistedString() throws if the preference is not attached to a hierarchy yet, like in the
        // layout preview of an ide, in which case the list is shown as empty.
        if (getPreferenceManager() == null || getKey() == null) return new ArrayList<>();

        try {
            return split(getPersistedString(getDefaultValue()));
        } catch (IllegalStateException e) {
            return new ArrayList<>();
        }
    }

    /** @return The summary of the preference for the selected {@code ids}. */
    @NonNull
    private CharSequence getSummaryFor(@NonNull List<String> ids) {
        if (ids.isEmpty()) return getContext().getString(com.termux.R.string.termux_ordered_items_none);

        List<Item> availableItems = getAvailableItems(getContext());
        StringBuilder summary = new StringBuilder();
        for (String id : ids) {
            Item item = findItem(availableItems, id);
            if (item == null) continue;
            if (summary.length() > 0) summary.append(", ");
            summary.append(item.label);
        }

        return summary;
    }

    /** Show the dialog that edits the list of items. */
    private void showItemsDialog() {
        Context context = getContext();
        List<Item> availableItems = getAvailableItems(context);
        List<String> selectedIds = getSelectedIds();

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        scrollView.addView(container, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if (getTitle() != null) builder.setTitle(getTitle());
        builder.setView(scrollView);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            persistString(join(selectedIds));
            // The summary is computed by getSummary(), so the list only has to be told to bind the
            // preference again. This is safe here, since the dialog is not part of the preference list.
            notifyChanged();
        });

        // The sections are rebuilt whenever an item is added, removed or moved, so that the dialog
        // always shows the current state of the list.
        Runnable rebuild = new Runnable() {
            @Override
            public void run() {
                container.removeAllViews();

                addSectionTitle(context, container, com.termux.R.string.termux_ordered_items_shown_header);
                if (selectedIds.isEmpty()) {
                    TextView empty = createLabel(context, context.getString(com.termux.R.string.termux_ordered_items_shown_empty));
                    empty.setTextColor(0x80808080);
                    container.addView(empty);
                }

                for (int i = 0; i < selectedIds.size(); i++) {
                    String id = selectedIds.get(i);
                    Item item = findItem(availableItems, id);
                    if (item == null) continue;
                    container.addView(createShownRow(context, item, i, selectedIds, this));
                }

                addSectionTitle(context, container, com.termux.R.string.termux_ordered_items_hidden_header);
                boolean hasHiddenItems = false;
                for (Item item : availableItems) {
                    if (selectedIds.contains(item.id)) continue;
                    hasHiddenItems = true;
                    container.addView(createHiddenRow(context, item, selectedIds, this));
                }

                if (!hasHiddenItems) {
                    TextView empty = createLabel(context, context.getString(com.termux.R.string.termux_ordered_items_hidden_empty));
                    empty.setTextColor(0x80808080);
                    container.addView(empty);
                }
            }
        };

        rebuild.run();
        builder.show();
    }

    /** @return The row of an item that is shown, with the buttons that change its order and remove it. */
    @NonNull
    private LinearLayout createShownRow(@NonNull Context context, @NonNull Item item, int index,
                                @NonNull List<String> selectedIds, @NonNull Runnable rebuild) {
        LinearLayout row = createRow(context, item);

        // The arrows are hidden instead of disabled, so that the rows of a list with a single item do
        // not look broken.
        if (index > 0)
            row.addView(createButton(context, "\u25b2", () -> {
                java.util.Collections.swap(selectedIds, index, index - 1);
                rebuild.run();
            }));

        if (index < selectedIds.size() - 1)
            row.addView(createButton(context, "\u25bc", () -> {
                java.util.Collections.swap(selectedIds, index, index + 1);
                rebuild.run();
            }));

        if (selectedIds.size() > getMinimumItemCount())
            row.addView(createButton(context, "\u2715", () -> {
                selectedIds.remove(index);
                rebuild.run();
            }));

        return row;
    }

    /** @return The row of an item that is not shown, which is added to the end of the list when clicked. */
    @NonNull
    private LinearLayout createHiddenRow(@NonNull Context context, @NonNull Item item,
                                 @NonNull List<String> selectedIds, @NonNull Runnable rebuild) {
        LinearLayout row = createRow(context, item);
        row.setOnClickListener(v -> {
            selectedIds.add(item.id);
            rebuild.run();
        });
        return row;
    }

    /** @return The view of an item, which shows its label on the left and its icon on the right. */
    @NonNull
    private LinearLayout createRow(@NonNull Context context, @NonNull Item item) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, ROW_PADDING_HORIZONTAL_DP), dp(context, ROW_PADDING_VERTICAL_DP),
            dp(context, ROW_PADDING_HORIZONTAL_DP), dp(context, ROW_PADDING_VERTICAL_DP));

        StateListDrawable background = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(0x20808080);
        background.addState(new int[]{android.R.attr.state_pressed}, pressed);
        background.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        row.setBackground(background);
        row.setClickable(true);

        TextView label = createLabel(context, item.label);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!item.icon.isEmpty()) {
            TextView icon = createLabel(context, item.icon);
            icon.setGravity(Gravity.CENTER_VERTICAL);
            icon.setPadding(dp(context, 8), 0, 0, 0);
            row.addView(icon);
        }

        return row;
    }

    /** @return A clickable button with a single character label, like an arrow of a shown item. */
    @NonNull
    private TextView createButton(@NonNull Context context, @NonNull String label, @NonNull Runnable onClick) {
        TextView button = createLabel(context, label);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(dp(context, BUTTON_MIN_SIZE_DP));
        button.setMinHeight(dp(context, BUTTON_MIN_SIZE_DP));
        button.setOnClickListener(v -> onClick.run());
        return button;
    }

    @NonNull
    private TextView createLabel(@NonNull Context context, @NonNull CharSequence text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    /** Add a section header, like the header of the shown or of the hidden items. */
    private void addSectionTitle(@NonNull Context context, @NonNull LinearLayout container, int titleRes) {
        TextView title = new TextView(context);
        title.setText(titleRes);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(context, ROW_PADDING_HORIZONTAL_DP), dp(context, 12),
            dp(context, ROW_PADDING_HORIZONTAL_DP), dp(context, 4));
        container.addView(title);
    }

    /** @return The item of the {@code availableItems} with the id {@code id}, or {@code null}. */
    @Nullable
    private static Item findItem(@NonNull List<Item> availableItems, @NonNull String id) {
        for (Item item : availableItems) {
            if (item.id.equals(id)) return item;
        }
        return null;
    }

    /** @return The comma separated {@code ids}, without duplicates and empty values. */
    @NonNull
    private static String join(@NonNull List<String> ids) {
        StringBuilder builder = new StringBuilder();
        for (String id : ids) {
            if (DataUtils.isNullOrEmpty(id)) continue;
            if (builder.length() > 0) builder.append(',');
            builder.append(id);
        }
        return builder.toString();
    }

    /** @return The comma separated {@code value} as a list, without empty values. */
    @NonNull
    private static List<String> split(@Nullable String value) {
        List<String> ids = new ArrayList<>();
        if (DataUtils.isNullOrEmpty(value)) return ids;

        for (String id : value.split(",")) {
            id = id.trim();
            if (id.isEmpty() || ids.contains(id)) continue;
            ids.add(id);
        }

        return ids;
    }

    private static int dp(@NonNull Context context, float dp) {
        return Math.round(ViewUtils.dpToPx(context, dp));
    }
}
