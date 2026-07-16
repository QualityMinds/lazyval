package com.qualityminds.lazyval.processor.internal;

import com.qualityminds.lazyval.processor.spi.Generator;
import org.jetbrains.annotations.UnknownNullability;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * Resolves which generators should actually run, given a set of candidates that may declare via
 * {@link Generator#supersedes()} that they render other generators redundant.
 *
 * <p>Concerned only with supersession: this class does not filter by classpath availability nor
 * apply user-authored {@code lazyval.generators.disable} configuration. Both are the caller's
 * responsibility and must be applied to {@code candidates} before calling {@link #resolve(Set)}.
 *
 * <p>Pure computation with no dependency on the annotation-processing environment, and therefore
 * unit-testable in isolation.
 */
class GeneratorResolution {

    /**
     * A supersession decision: {@code id} was dropped from the active set because {@code by} — itself
     * active — declared it in its {@link Generator#supersedes()}.
     */
    record Superseded(String id, String by) {}

    /**
     * Outcome of {@link #resolve(Set)}: the generators that survived supersession and the events
     * describing which candidates were dropped and by whom. Iteration order of both sets follows the
     * iteration order of the {@code candidates} passed to {@code resolve}.
     */
    record Result(Set<? extends Generator> active, Set<Superseded> superseded) {}

    /**
     * Partitions {@code candidates} into active generators and supersession events based on their
     * {@link Generator#supersedes()} declarations.
     *
     * <h4>Contract</h4>
     * <ul>
     *   <li><b>Active status:</b> a candidate is active if no <em>active</em> candidate lists its id
     *       in {@link Generator#supersedes()}. Because the rule is self-referential, supersedes claims
     *       made by a dropped candidate have no effect. For example, if {@code a} supersedes {@code b}
     *       and {@code b} supersedes {@code c}, then {@code b} is dropped by {@code a} and its claim on
     *       {@code c} is moot — {@code c} remains active.</li>
     *   <li><b>Unknown targets:</b> ids in {@code supersedes()} that do not match any candidate are
     *       silently ignored (no error, no event). Generators can safely declare supersession over ids
     *       that may not be present.</li>
     *   <li><b>Multiple superseders:</b> if a candidate is superseded by several active candidates,
     *       one {@link Superseded} record is emitted per superseding pair.</li>
     *   <li><b>Order preservation:</b> iteration order of {@code candidates} is preserved in
     *       {@link Result#active()} and {@link Result#superseded()}. Callers wanting stable output
     *       should pass an ordered set.</li>
     * </ul>
     *
     * @param candidates generators to resolve; must already have been filtered for classpath
     *                   availability and user-disable configuration.
     * @return the partition into surviving generators and supersession events.
     * @throws IllegalStateException if the superseded graph among {@code candidates} contains a cycle.
     */
    static Result resolve(@UnknownNullability Set<? extends Generator> candidates) {
        var byId = candidates.stream().collect(toMap(Generator::generatorId, identity()));

        Map<String, Set<String>> superseders = new HashMap<>();
        for (var g : candidates) {
            for (var superseded : g.supersedes()) {
                if (byId.containsKey(superseded)) {
                    superseders.computeIfAbsent(superseded, k -> new LinkedHashSet<>()).add(g.generatorId());
                }
            }
        }

        Map<String, Boolean> isActive = new HashMap<>();
        Set<String> visiting = new LinkedHashSet<>();
        for (var g : candidates) {
            computeActive(g.generatorId(), superseders, isActive, visiting);
        }

        Set<Generator> active = new LinkedHashSet<>();
        Set<Superseded> superseded = new LinkedHashSet<>();
        for (var g : candidates) {
            if (isActive.get(g.generatorId())) {
                active.add(g);
            } else {
                for (var supersederId : superseders.getOrDefault(g.generatorId(), Set.of())) {
                    if (Boolean.TRUE.equals(isActive.get(supersederId))) {
                        superseded.add(new Superseded(g.generatorId(), supersederId));
                    }
                }
            }
        }
        return new Result(active, superseded);
    }

    private static boolean computeActive(
            String id,
            Map<String, Set<String>> superseders,
            Map<String, Boolean> isActive,
            Set<String> visiting) {
        var cached = isActive.get(id);
        if (cached != null) return cached;
        if (!visiting.add(id)) {
            throw new IllegalStateException(
                    "Cycle detected in generator supersedes graph involving '" + id + "'; visiting: " + visiting);
        }
        try {
            for (var supersederId : superseders.getOrDefault(id, Set.of())) {
                if (computeActive(supersederId, superseders, isActive, visiting)) {
                    isActive.put(id, false);
                    return false;
                }
            }
            isActive.put(id, true);
            return true;
        } finally {
            visiting.remove(id);
        }
    }

    private GeneratorResolution() {}
}
