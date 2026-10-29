package com.termux.app.fragments.settings.termux;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Tests the conversion between the sets of a {@code MultiSelectListPreference} and the comma separated
 * value of a property, since the difference between an empty value and a missing property decides
 * whether the configuration uses the default value of the preference or nothing at all.
 */
public class TermuxPropertyPreferenceDataStoreTest {

    @Test
    public void testJoinSortsTheValues() {
        Set<String> values = new LinkedHashSet<>();
        values.add("title");
        values.add("bell");
        values.add("session-exit");

        Assert.assertEquals("bell,session-exit,title", TermuxPropertyPreferenceDataStore.joinStringSet(values));
    }

    @Test
    public void testJoinOfAnEmptySetIsAnEmptyValue() {
        // An empty value keeps the property in the file, so that the configuration does not fall back to
        // its default value, which may select some items again.
        Assert.assertEquals("", TermuxPropertyPreferenceDataStore.joinStringSet(new LinkedHashSet<>()));
    }

    @Test
    public void testJoinOfNoSetIsNull() {
        // A null set means that the property must be commented out, so that the default value is used.
        Assert.assertNull(TermuxPropertyPreferenceDataStore.joinStringSet(null));
    }

    @Test
    public void testSplit() {
        Assert.assertEquals(new TreeSet<>(Collections.singletonList("bell")),
            new TreeSet<>(TermuxPropertyPreferenceDataStore.splitStringSet("bell")));

        Set<String> values = TermuxPropertyPreferenceDataStore.splitStringSet("bell, plugin-result ,,bell");
        Assert.assertEquals(2, values.size());
        Assert.assertTrue(values.contains("bell"));
        Assert.assertTrue(values.contains("plugin-result"));
    }

    @Test
    public void testSplitOfAnEmptyValueIsEmpty() {
        // An empty value means that the user selected nothing, it must not return null.
        Set<String> values = TermuxPropertyPreferenceDataStore.splitStringSet("");
        Assert.assertNotNull(values);
        Assert.assertTrue(values.isEmpty());
    }

    @Test
    public void testRoundTrip() {
        Set<String> values = TermuxPropertyPreferenceDataStore.splitStringSet("title,bell");
        String joined = TermuxPropertyPreferenceDataStore.joinStringSet(values);
        Assert.assertEquals(values, TermuxPropertyPreferenceDataStore.splitStringSet(joined));
    }
}
