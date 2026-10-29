package com.termux.app.fragments.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalColors;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Applies the accent color of the terminal color scheme to the widgets of the settings screen, like the
 * switches, the sliders and the titles of the categories, so that the settings look like the terminal instead
 * of using the accent color of the app theme.
 *
 * The settings screens keep the light or dark material theme, since there are many material widgets that
 * would be hard to read with a color scheme that has a very bright background, only the accent color is taken
 * from the color scheme.
 *
 * The color is applied to the widgets of the rows after they are bound, since android theme attributes cannot
 * be changed at runtime, and the rows of the list are recycled, so the widgets are tinted whenever a row is
 * attached.
 */
public final class PreferenceAccentUtils {

    private static final String LOG_TAG = "PreferenceAccentUtils";

    /** The index of the `color4` (blue) of the palette, which is the accent color of the rest of the app. */
    private static final int ACCENT_COLOR_INDEX = 4;

    /** The colors of the unchecked parts of the widgets, which are the grey of the material theme. */
    private static final int UNCHECKED_THUMB_COLOR = 0xFFBDBDBD;
    private static final int UNCHECKED_TRACK_COLOR = 0x4DBDBDBD;

    private PreferenceAccentUtils() {
    }

    /**
     * Tint the widgets of the screen of {@code fragment} and the titles of its categories with the accent
     * color of the color scheme, which must be called after the screen was created from its resource.
     */
    public static void applyAccent(@NonNull PreferenceFragmentCompat fragment) {
        Context context = fragment.getContext();
        if (context == null) return;

        tintCategoryTitles(fragment.getPreferenceScreen(), getAccentColor(context));
        runWhenListCreated(fragment);
    }

    /** @return The accent color of the current color scheme, which is read from the `colors.properties` file. */
    public static int getAccentColor(@NonNull Context context) {
        loadColorScheme();

        int[] colors = TerminalColors.COLOR_SCHEME.mDefaultColors;
        if (colors != null && colors.length > ACCENT_COLOR_INDEX) return colors[ACCENT_COLOR_INDEX];

        // Fall back to a blue when the scheme has no palette, which the default color scheme does not do.
        return 0xFF2196F3;
    }

    /** Read the color scheme of Termux:Styling, since the settings screen may be started on its own. */
    private static void loadColorScheme() {
        Properties properties = new Properties();
        File colorsFile = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
        if (!colorsFile.isFile()) return;

        try (InputStream inputStream = new FileInputStream(colorsFile);
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            properties.load(reader);
            TerminalColors.COLOR_SCHEME.updateWith(properties);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read the color scheme", e);
        }
    }

    /** Color the titles of the categories of {@code group}, which are drawn by the preference itself. */
    private static void tintCategoryTitles(@Nullable PreferenceGroup group, int accentColor) {
        if (group == null) return;

        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            if (preference instanceof PreferenceCategory) {
                tintCategoryTitle(preference, accentColor);
                tintCategoryTitles((PreferenceGroup) preference, accentColor);
                continue;
            }

            if (preference instanceof PreferenceGroup)
                tintCategoryTitles((PreferenceGroup) preference, accentColor);
        }
    }

    /** Color the title of the category {@code preference} with the accent color. */
    private static void tintCategoryTitle(@NonNull Preference preference, int accentColor) {
        CharSequence title = preference.getTitle();
        if (title == null || title.length() == 0) return;

        SpannableString coloredTitle = new SpannableString(title);
        coloredTitle.setSpan(new ForegroundColorSpan(accentColor),
            0, coloredTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        preference.setTitle(coloredTitle);
    }

    /** Tint the rows of the list once it exists, and every row that is attached to it afterwards. */
    private static void runWhenListCreated(@NonNull PreferenceFragmentCompat fragment) {
        RecyclerView list = fragment.getListView();
        if (list != null) {
            setUpList(fragment, list);
            return;
        }

        // The view of the fragment is created after its preferences, so wait for it once.
        fragment.getViewLifecycleOwnerLiveData().observe(fragment, owner -> {
            RecyclerView createdList = fragment.getListView();
            if (createdList != null) setUpList(fragment, createdList);
        });
    }

    private static void setUpList(@NonNull PreferenceFragmentCompat fragment, @NonNull RecyclerView list) {
        if (list == null || fragment.getContext() == null) return;

        final int accentColor = getAccentColor(fragment.requireContext());

        list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                tintView(view, accentColor);
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
                // The row is recycled, so it is tinted again when it is attached with another preference.
            }
        });

        for (int i = 0; i < list.getChildCount(); i++) tintView(list.getChildAt(i), accentColor);
    }

    /** Tint the widgets of {@code view} and of its children with {@code accentColor}. */
    private static void tintView(@NonNull View view, int accentColor) {
        if (view instanceof SwitchCompat) {
            SwitchCompat switchView = (SwitchCompat) view;
            switchView.setThumbTintList(twoStateColorList(accentColor, UNCHECKED_THUMB_COLOR));
            switchView.setTrackTintList(twoStateColorList(withAlpha(accentColor, 0x80), UNCHECKED_TRACK_COLOR));
            return;
        }

        if (view instanceof SeekBar) {
            SeekBar seekBar = (SeekBar) view;
            seekBar.setProgressTintList(ColorStateList.valueOf(accentColor));
            seekBar.setThumbTintList(ColorStateList.valueOf(accentColor));
            seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(withAlpha(accentColor, 0x3D)));
            return;
        }

        if (view instanceof CompoundButton) {
            ((CompoundButton) view).setButtonTintList(twoStateColorList(accentColor, UNCHECKED_THUMB_COLOR));
            return;
        }

        if (view instanceof ViewGroup) {
            android.view.ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) tintView(group.getChildAt(i), accentColor);
        }
    }

    /** @return A list that uses {@code checkedColor} when the widget is checked and {@code otherColor} otherwise. */
    @NonNull
    private static ColorStateList twoStateColorList(int checkedColor, int otherColor) {
        return new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
            new int[]{checkedColor, otherColor});
    }

    /** @return {@code color} with its alpha replaced by {@code alpha}. */
    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
