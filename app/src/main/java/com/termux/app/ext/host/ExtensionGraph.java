package com.termux.app.ext.host;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves which extensions may run, in which order, and why the others may not.
 *
 * <p>The resolver is a pure function of the extension specs and the enabled flags, so that the dependency
 * rules can be tested without an Android runtime. The host feeds it the specs and the user preferences and
 * only starts the extensions whose state is {@link ExtensionState#ENABLED}, in the returned order, which
 * guarantees that a dependency is started before its dependents.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *     <li>an extension that is not user disableable is always enabled, whatever the stored flag says;</li>
 *     <li>an extension whose stored flag is absent uses its default;</li>
 *     <li>a dependency that is missing or not running makes the dependent unavailable;</li>
 *     <li>a dependency cycle makes every extension in the cycle unavailable, instead of hanging the host.</li>
 * </ul>
 */
public final class ExtensionGraph {

    private ExtensionGraph() {
    }

    /**
     * Resolve the state of every extension.
     *
     * @param specs       The registered extensions, in registration order.
     * @param userEnabled The stored enabled flags, keyed by extension id; absent keys use the default.
     * @return One status per spec, in an order where dependencies come before their dependents.
     */
    @NonNull
    public static List<ExtensionStatus> resolve(@NonNull List<ExtensionSpec> specs,
                                                @NonNull Map<String, Boolean> userEnabled) {
        Map<String, ExtensionSpec> byId = index(specs);
        Set<String> cycles = findCycles(specs, byId);

        // Dependencies may be declared before or after their dependents, so the state of an extension is
        // resolved recursively and memoized instead of relying on the registration order.
        Map<String, ExtensionStatus> resolved = new LinkedHashMap<>();
        for (ExtensionSpec spec : specs) {
            resolveOne(spec, byId, userEnabled, resolved, cycles);
        }
        return topologicalOrder(specs, byId, resolved);
    }

    @NonNull
    private static ExtensionStatus resolveOne(@NonNull ExtensionSpec spec,
                                              @NonNull Map<String, ExtensionSpec> byId,
                                              @NonNull Map<String, Boolean> userEnabled,
                                              @NonNull Map<String, ExtensionStatus> resolved,
                                              @NonNull Set<String> cycles) {
        ExtensionStatus memo = resolved.get(spec.id);
        if (memo != null) return memo;

        ExtensionStatus status;
        if (!spec.userDisableable) {
            status = new ExtensionStatus(spec.id, spec.requires, false, ExtensionState.ENABLED, null);
        } else {
            boolean enabled = userEnabled.containsKey(spec.id)
                ? userEnabled.get(spec.id)
                : spec.enabledByDefault;
            if (!enabled) {
                status = new ExtensionStatus(spec.id, spec.requires, true, ExtensionState.DISABLED, null);
            } else if (cycles.contains(spec.id)) {
                status = new ExtensionStatus(spec.id, spec.requires, true,
                    ExtensionState.DEPENDENCY_CYCLE, null);
            } else {
                status = new ExtensionStatus(spec.id, spec.requires, true, ExtensionState.ENABLED, null);
                for (String required : spec.requires) {
                    ExtensionSpec dependency = byId.get(required);
                    if (dependency == null) {
                        status = new ExtensionStatus(spec.id, spec.requires, true,
                            ExtensionState.MISSING_DEPENDENCY, required);
                        break;
                    }
                    ExtensionStatus dependencyStatus =
                        resolveOne(dependency, byId, userEnabled, resolved, cycles);
                    if (!dependencyStatus.state.isRunning()) {
                        status = new ExtensionStatus(spec.id, spec.requires, true,
                            ExtensionState.MISSING_DEPENDENCY, required);
                        break;
                    }
                }
            }
        }
        resolved.put(spec.id, status);
        return status;
    }

    /** The extensions that depend on {@code id}, directly or transitively. */
    @NonNull
    public static List<String> dependentsOf(@NonNull List<ExtensionSpec> specs, @NonNull String id) {
        List<String> dependents = new ArrayList<>();
        Map<String, ExtensionSpec> byId = index(specs);
        for (ExtensionSpec spec : specs) {
            if (spec.id.equals(id)) continue;
            if (dependsOn(spec, id, byId, new HashSet<>())) dependents.add(spec.id);
        }
        return dependents;
    }

    private static boolean dependsOn(ExtensionSpec spec, String target, Map<String, ExtensionSpec> byId,
                                     Set<String> visited) {
        if (!visited.add(spec.id)) return false;
        for (String required : spec.requires) {
            if (required.equals(target)) return true;
            ExtensionSpec dependency = byId.get(required);
            if (dependency != null && dependsOn(dependency, target, byId, visited)) return true;
        }
        return false;
    }

    /** Depth-first topological order that keeps registration order between independent extensions. */
    private static List<ExtensionStatus> topologicalOrder(List<ExtensionSpec> specs,
                                                          Map<String, ExtensionSpec> byId,
                                                          Map<String, ExtensionStatus> resolved) {
        List<ExtensionStatus> ordered = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();
        for (ExtensionSpec spec : specs) {
            emit(spec, byId, resolved, ordered, emitted, new HashSet<>());
        }
        return ordered;
    }

    private static void emit(ExtensionSpec spec, Map<String, ExtensionSpec> byId,
                             Map<String, ExtensionStatus> resolved, List<ExtensionStatus> ordered,
                             Set<String> emitted, Set<String> visiting) {
        if (emitted.contains(spec.id) || !visiting.add(spec.id)) return;
        for (String required : spec.requires) {
            ExtensionSpec dependency = byId.get(required);
            if (dependency != null) emit(dependency, byId, resolved, ordered, emitted, visiting);
        }
        visiting.remove(spec.id);
        ExtensionStatus status = resolved.get(spec.id);
        if (status != null && emitted.add(spec.id)) ordered.add(status);
    }

    private static Map<String, ExtensionSpec> index(List<ExtensionSpec> specs) {
        Map<String, ExtensionSpec> byId = new HashMap<>();
        for (ExtensionSpec spec : specs) byId.put(spec.id, spec);
        return byId;
    }

    /** The identifiers that take part in at least one dependency cycle. */
    private static Set<String> findCycles(List<ExtensionSpec> specs, Map<String, ExtensionSpec> byId) {
        Set<String> inCycle = new HashSet<>();
        for (ExtensionSpec spec : specs) {
            if (reaches(spec.id, spec.id, byId, new HashSet<>())) inCycle.add(spec.id);
        }
        return inCycle;
    }

    private static boolean reaches(String from, String target, Map<String, ExtensionSpec> byId,
                                   Set<String> visited) {
        ExtensionSpec spec = byId.get(from);
        if (spec == null) return false;
        for (String required : spec.requires) {
            if (required.equals(target)) return true;
            if (!visited.add(required)) continue;
            if (reaches(required, target, byId, visited)) return true;
        }
        return false;
    }
}
