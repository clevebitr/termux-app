package com.termux.app.fragments.settings.termux;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.shared.data.DataUtils;

/**
 * A {@link Preference} that allows the user to set a color value, like the colors of the extra keys
 * view buttons, by selecting one from a predefined palette, by entering a custom color value or by
 * resetting it, so that the default value from the termux color scheme is used again.
 *
 * The color value is persisted as a string like `#ff000000` and is shown as the summary of the
 * preference. The preference must be used with a {@link androidx.preference.PreferenceDataStore}
 * that persists the value in the `termux.properties` file, like the one used by
 * {@link TerminalKeyboardPreferencesFragment}.
 */
@Keep
public class ColorPreference extends Preference {

    /**
     * The color values that can be selected from the palette. The first ones are the common opaque
     * colors and the last ones are semi-transparent colors, which can be used to let the terminal
     * background show through the extra keys view.
     */
    public static final String[] COLOR_PALETTE = new String[] {
        "#ff000000", // black
        "#ff303030", // dark grey
        "#ff505050", // grey
        "#ff9e9e9e", // light grey
        "#ffd0d0d0", // very light grey
        "#ffffffff", // white
        "#ffff5252", // red
        "#ffff9800", // orange
        "#ffffeb3b", // yellow
        "#ff8bc34a", // light green
        "#ff00bcd4", // cyan
        "#ff2196f3", // blue
        "#ff3f51b5", // indigo
        "#ff9c27b0", // purple
        "#ffe91e63", // pink
        "#ff009688", // teal
        "#ff795548", // brown
        "#00000000", // fully transparent
        "#80000000", // semi-transparent black
        "#80ffffff", // semi-transparent white
        "#33000000", // mostly transparent black
        "#33ffffff", // mostly transparent white
    };

    /**
     * The palette dialog that is currently being shown, if any, so that the color swatch click
     * listeners can dismiss it.
     */
    @Nullable
    private AlertDialog mPaletteDialog;

    public ColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ColorPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ColorPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        updateSummary();
    }

    @Override
    protected void onClick() {
        showColorPaletteDialog();
    }

    /** Get the currently persisted color value, or {@code null} if it is not set. */
    @Nullable
    private String getCurrentColorValue() {
        String value = getPersistedString(null);
        return DataUtils.isNullOrEmpty(value) ? null : value;
    }

    /** Persist the {@code colorValue}, or reset it if {@code colorValue} is {@code null}. */
    private void setColorValue(@Nullable String colorValue) {
        persistString(colorValue);
        updateSummary();
    }

    /** Update the summary to show the currently selected color value, if any. */
    private void updateSummary() {
        Context context = getContext();
        if (context == null) return;

        String value = getCurrentColorValue();
        setSummary(value != null ? value : context.getString(R.string.termux_extra_keys_color_follow_theme));
    }

    private void showColorPaletteDialog() {
        final Context context = getContext();
        if (context == null) return;

        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(getTitle())
            .setView(createColorPaletteView(context))
            .setPositiveButton(R.string.termux_extra_keys_color_custom, null)
            .setNeutralButton(R.string.termux_extra_keys_color_follow_theme, (d, which) -> setColorValue(null))
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
            v -> showCustomColorValueDialog(context, dialog)));

        mPaletteDialog = dialog;
        dialog.setOnDismissListener(d -> mPaletteDialog = null);
        dialog.show();
    }

    /** Create a scrollable grid of color swatches that persist the selected color on click. */
    @NonNull
    private View createColorPaletteView(@NonNull Context context) {
        final String currentValue = getCurrentColorValue();
        final float density = context.getResources().getDisplayMetrics().density;

        int swatchSize = Math.round(44 * density);
        int swatchMargin = Math.round(4 * density);
        int borderWidth = Math.round(1 * density);
        int selectedBorderWidth = Math.round(3 * density);
        int padding = Math.round(16 * density);

        int selectedBorderColor = context.getResources().getColor(R.color.blue_link_dark);
        int borderColor = context.getResources().getColor(R.color.grey_500);

        GridLayout gridLayout = new GridLayout(context);
        gridLayout.setColumnCount(6);
        gridLayout.setPadding(padding, padding, padding, padding);

        for (String colorValue : COLOR_PALETTE) {
            boolean isSelected = colorValue.equalsIgnoreCase(currentValue);

            GradientDrawable swatchDrawable = new GradientDrawable();
            swatchDrawable.setShape(GradientDrawable.RECTANGLE);
            swatchDrawable.setColor(Color.parseColor(colorValue));
            swatchDrawable.setStroke(isSelected ? selectedBorderWidth : borderWidth,
                isSelected ? selectedBorderColor : borderColor);

            View swatch = new View(context);
            swatch.setBackground(swatchDrawable);
            swatch.setContentDescription(colorValue);

            GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams();
            layoutParams.width = swatchSize;
            layoutParams.height = swatchSize;
            layoutParams.setMargins(swatchMargin, swatchMargin, swatchMargin, swatchMargin);
            swatch.setLayoutParams(layoutParams);

            swatch.setOnClickListener(v -> {
                setColorValue(colorValue);
                if (mPaletteDialog != null) mPaletteDialog.dismiss();
            });

            gridLayout.addView(swatch);
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(gridLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    /** Show a dialog that allows the user to enter a custom color value. */
    private void showCustomColorValueDialog(@NonNull Context context, @NonNull AlertDialog paletteDialog) {
        final String currentValue = getCurrentColorValue();

        final EditText editText = new EditText(context);
        editText.setSingleLine(true);
        editText.setHint("#RRGGBB");
        if (currentValue != null) editText.setText(currentValue);
        editText.setSelection(editText.getText().length());

        final AlertDialog customDialog = new AlertDialog.Builder(context)
            .setTitle(R.string.termux_extra_keys_color_custom)
            .setView(editText)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        customDialog.setOnShowListener(d -> customDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = editText.getText().toString().trim();

            if (!value.isEmpty()) {
                try {
                    // Validate the color value, like `#RGB`, `#ARGB`, `#RRGGBB` or `#AARRGGBB`.
                    Color.parseColor(value);
                } catch (IllegalArgumentException e) {
                    Toast.makeText(context, context.getString(R.string.termux_extra_keys_color_invalid, value), Toast.LENGTH_LONG).show();
                    return;
                }
            }

            setColorValue(value.isEmpty() ? null : value);
            customDialog.dismiss();
            paletteDialog.dismiss();
        }));

        customDialog.show();
    }

    @Override
    protected void onPrepareForRemoval() {
        super.onPrepareForRemoval();
        mPaletteDialog = null;
    }

}
