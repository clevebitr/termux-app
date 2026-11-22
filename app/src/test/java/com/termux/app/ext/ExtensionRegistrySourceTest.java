package com.termux.app.ext;

import com.termux.app.ext.api.Extension;
import com.termux.app.ext.api.ExtensionSettingsPage;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Guards that an extension declares what it really owns.
 *
 * <p>Both checks are source level because the failure they prevent is silent: a preference key that does not
 * exist in the XML leaves the entry of a disabled extension visible, and a page that does not check its
 * extension still works after the user disabled it. Neither can be caught by starting the app in a unit
 * test, which has no preference screen.</p>
 */
public class ExtensionRegistrySourceTest {

    /** The preference XML of the root settings page, where the entries of the extensions are declared. */
    private static final String TERMUX_PREFERENCES_XML = "src/main/res/xml/termux_preferences.xml";

    /** The root of the Java sources, which the tests resolve relative to the module directory. */
    private static final String JAVA_SOURCE_ROOT = "src/main/java/";

    private static String read(Path path) throws IOException {
        Assert.assertTrue("The file must be readable for this test, looked in \""
            + path.toAbsolutePath() + "\"", Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourceOf(String className) {
        return Paths.get(JAVA_SOURCE_ROOT + className.replace('.', '/') + ".java");
    }

    /**
     * A preference key that the extension declares has to exist on the root settings page, otherwise the
     * visibility pass cannot hide the entry and a disabled feature keeps a working entrance.
     */
    @Test
    public void testDeclaredPreferenceKeysExistInTheSettingsXml() throws IOException {
        String xml = read(Paths.get(TERMUX_PREFERENCES_XML));
        for (Extension extension : ForkExtensions.installed()) {
            for (String key : extension.preferenceKeys()) {
                Assert.assertTrue("The extension \"" + extension.id() + "\" declares the preference key \""
                        + key + "\", which no entry of " + TERMUX_PREFERENCES_XML + " uses",
                    xml.contains("app:key=\"" + key + "\""));
            }
        }
    }

    /**
     * Every declared page must exist and be gated, since the settings search and a restored back stack reach a
     * page without the entry of the root screen, which is the only place where an entry is hidden.
     */
    @Test
    public void testDeclaredSettingsPagesExistAndGateTheirFeature() throws IOException {
        for (Extension extension : ForkExtensions.installed()) {
            for (ExtensionSettingsPage page : extension.settingsPages()) {
                Path source = sourceOf(page.fragmentClassName);
                String text = read(source);

                Assert.assertTrue("The page \"" + page.fragmentClassName + "\" of the extension \""
                    + extension.id() + "\" must check whether its extension runs", text.contains("isRunning("));
            }
        }
    }

    /** Two extensions must not declare the same page or the same page title, or the search cannot tell them apart. */
    @Test
    public void testDeclaredSettingsPagesAreUnique() {
        Set<String> fragmentClassNames = new HashSet<>();
        Set<Integer> xmlResources = new HashSet<>();
        for (Extension extension : ForkExtensions.installed()) {
            for (ExtensionSettingsPage page : extension.settingsPages()) {
                Assert.assertTrue("Duplicate settings page: " + page.fragmentClassName,
                    fragmentClassNames.add(page.fragmentClassName));
                Assert.assertTrue("Duplicate settings page XML of " + page.fragmentClassName,
                    xmlResources.add(page.xmlRes));
                Assert.assertNotEquals("The page \"" + page.fragmentClassName + "\" has no title",
                    0, page.titleRes);
            }
        }
    }
}
