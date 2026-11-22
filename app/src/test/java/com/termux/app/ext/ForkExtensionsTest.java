package com.termux.app.ext;

import com.termux.app.ext.api.Extension;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Checks that the extension registry stays consistent as features are migrated. */
public class ForkExtensionsTest {

    @Test
    public void idsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (Extension extension : ForkExtensions.installed()) {
            assertTrue("Duplicate extension id: " + extension.id(), ids.add(extension.id()));
        }
    }

    @Test
    public void dependenciesExist() {
        Set<String> ids = new HashSet<>();
        for (Extension extension : ForkExtensions.installed()) ids.add(extension.id());
        for (Extension extension : ForkExtensions.installed()) {
            for (String required : extension.requires()) {
                assertTrue(extension.id() + " requires unknown extension " + required,
                    ids.contains(required));
                assertFalse(extension.id() + " must not require itself", extension.id().equals(required));
            }
        }
    }

    @Test
    public void preferenceKeysAreUniqueAndNonEmpty() {
        Set<String> keys = new HashSet<>();
        for (Extension extension : ForkExtensions.installed()) {
            List<String> preferences = new ArrayList<>(extension.preferenceKeys());
            for (String key : preferences) {
                assertFalse("Empty preference key in " + extension.id(), key.isEmpty());
                assertTrue("Duplicate preference key: " + key, keys.add(key));
            }
        }
    }

    @Test
    public void backupExtensionIsRegisteredAndEnabledByDefault() {
        Extension backup = null;
        for (Extension extension : ForkExtensions.installed()) {
            if ("backup".equals(extension.id())) backup = extension;
        }
        assertTrue("backup extension is registered", backup != null);
        assertTrue("backup is user disableable", backup.isUserDisableable());
        assertTrue("backup is enabled by default", backup.isEnabledByDefault());
        assertEquals(1, backup.preferenceKeys().size());
    }
}
