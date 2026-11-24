package com.termux.app.ext;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Guards that every class name written in a resource file still exists.
 *
 * <p>A custom view in a layout and the {@code app:fragment} of a preference page are class names in a string, so
 * the compiler never sees them: moving a class leaves the resource pointing at the old package, the build stays
 * green, and the app crashes at runtime with {@code ClassNotFoundException} while inflating the layout or opening
 * the page. This test turns that crash into a test failure.</p>
 */
public class ResourceClassNameTest {

    /** The directories that may name a class: layouts, preference pages and the other XML resources. */
    private static final String[] RESOURCE_DIRECTORIES = new String[]{
        "src/main/res/layout", "src/main/res/xml", "src/main/res/menu",
    };

    /** A fully qualified class name, whose last segment therefore starts with an upper case letter. */
    private static final Pattern CLASS_NAME = Pattern.compile("com\\.termux(?:\\.[A-Za-z0-9_]+)+");

    private static List<Path> resourceFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String directory : RESOURCE_DIRECTORIES) {
            Path root = Paths.get(directory);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .forEach(files::add);
            }
        }
        return files;
    }

    private static boolean looksLikeClassName(String candidate) {
        int lastDot = candidate.lastIndexOf('.');
        if (lastDot < 0 || lastDot + 1 >= candidate.length()) return false;
        return Character.isUpperCase(candidate.charAt(lastDot + 1));
    }

    @Test
    public void testEveryClassNameInTheResourcesExists() throws Exception {
        TreeSet<String> missing = new TreeSet<>();
        List<Path> files = resourceFiles();
        Assert.assertFalse("No resource file was found, the guard would silently pass", files.isEmpty());

        for (Path file : files) {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Matcher matcher = CLASS_NAME.matcher(text);
            while (matcher.find()) {
                String candidate = matcher.group();
                if (!looksLikeClassName(candidate)) continue;
                try {
                    Class.forName(candidate);
                } catch (ClassNotFoundException e) {
                    missing.add(candidate + " (in " + file + ")");
                }
            }
        }

        Assert.assertTrue("A resource names a class that does not exist, which crashes at runtime:\n"
            + String.join("\n", missing), missing.isEmpty());
    }
}
