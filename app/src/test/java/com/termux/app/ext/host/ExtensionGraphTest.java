package com.termux.app.ext.host;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The dependency, default and cascade rules of the extension graph. */
public class ExtensionGraphTest {

    private static ExtensionSpec spec(String id, boolean userDisableable, boolean enabledByDefault,
                                      String... requires) {
        return new ExtensionSpec(id, Arrays.asList(requires), userDisableable, enabledByDefault);
    }

    private static Map<String, ExtensionStatus> resolve(List<ExtensionSpec> specs,
                                                        Map<String, Boolean> flags) {
        Map<String, ExtensionStatus> byId = new HashMap<>();
        for (ExtensionStatus status : ExtensionGraph.resolve(specs, flags)) {
            byId.put(status.id, status);
        }
        return byId;
    }

    @Test
    public void defaultsApplyWhenTheUserHasNotChosen() {
        List<ExtensionSpec> specs = Collections.singletonList(spec("statusbar", true, true));

        assertTrue(resolve(specs, Collections.emptyMap()).get("statusbar").state.isRunning());
        assertFalse(resolve(specs, Collections.singletonMap("statusbar", false))
            .get("statusbar").state.isRunning());
    }

    @Test
    public void coreExtensionCannotBeDisabled() {
        List<ExtensionSpec> specs = Collections.singletonList(spec("core", false, true));

        ExtensionStatus status = resolve(specs, Collections.singletonMap("core", false)).get("core");

        assertEquals(ExtensionState.ENABLED, status.state);
        assertFalse(status.userDisableable);
    }

    @Test
    public void missingDependencyKeepsTheDependentStopped() {
        List<ExtensionSpec> specs = Arrays.asList(
            spec("notifications", true, true, "statusbar"),
            spec("statusbar", true, false));

        Map<String, ExtensionStatus> byId = resolve(specs, Collections.emptyMap());

        assertEquals(ExtensionState.MISSING_DEPENDENCY, byId.get("notifications").state);
        assertEquals("statusbar", byId.get("notifications").missingDependency);
        assertEquals(ExtensionState.DISABLED, byId.get("statusbar").state);
    }

    @Test
    public void dependencyIsOrderedBeforeItsDependent() {
        List<ExtensionSpec> specs = Arrays.asList(
            spec("notifications", true, true, "statusbar"),
            spec("statusbar", true, true));

        List<String> order = new ArrayList<>();
        for (ExtensionStatus status : ExtensionGraph.resolve(specs, Collections.emptyMap())) {
            order.add(status.id);
        }

        assertEquals(Arrays.asList("statusbar", "notifications"), order);
    }

    @Test
    public void enabledDependentsCascadeOnDisable() {
        List<ExtensionSpec> specs = Arrays.asList(
            spec("statusbar", true, true),
            spec("notifications", true, true, "statusbar"),
            spec("palette", true, true));

        assertEquals(Collections.singletonList("notifications"),
            ExtensionGraph.dependentsOf(specs, "statusbar"));
        assertTrue(ExtensionGraph.dependentsOf(specs, "palette").isEmpty());
    }

    @Test
    public void cycleIsReportedInsteadOfHanging() {
        List<ExtensionSpec> specs = Arrays.asList(
            spec("a", true, true, "b"),
            spec("b", true, true, "a"));

        Map<String, ExtensionStatus> byId = resolve(specs, Collections.emptyMap());

        assertEquals(ExtensionState.DEPENDENCY_CYCLE, byId.get("a").state);
        assertEquals(ExtensionState.DEPENDENCY_CYCLE, byId.get("b").state);
        assertFalse(byId.get("a").state.isRunning());
    }

    @Test
    public void unknownDependencyMarksTheExtensionUnavailable() {
        List<ExtensionSpec> specs = Collections.singletonList(spec("notifications", true, true, "missing"));

        assertEquals(ExtensionState.MISSING_DEPENDENCY,
            resolve(specs, Collections.emptyMap()).get("notifications").state);
    }
}
