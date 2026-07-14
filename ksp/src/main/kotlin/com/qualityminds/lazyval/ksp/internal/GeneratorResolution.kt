package com.qualityminds.lazyval.ksp.internal

import com.qualityminds.lazyval.ksp.internal.GeneratorResolution.resolve
import com.qualityminds.lazyval.ksp.spi.Generator

/**
 * Resolves which generators should actually run, given a set of candidates that may declare via
 * [Generator.supersedes] that they render other generators redundant.
 *
 * Concerned only with supersession: this object does not filter by classpath availability nor
 * apply user-authored `lazyval.generators.disable` configuration. Both are the caller's
 * responsibility and must be applied to `candidates` before calling [resolve].
 *
 * Pure computation with no dependency on the annotation-processing environment, and therefore
 * unit-testable in isolation.
 */
internal object GeneratorResolution {

    /**
     * A supersession decision: [id] was dropped from the active set because [by] — itself active —
     * declared it in its [Generator.supersedes].
     */
    data class Superseded(val id: String, val by: String)

    /**
     * Outcome of [resolve]: the generators that survived supersession and the events describing
     * which candidates were dropped and by whom. Iteration order of both sets follows the
     * iteration order of the `candidates` passed to [resolve].
     */
    data class Result(val active: Set<Generator>, val superseded: Set<Superseded>)

    /**
     * Partitions [candidates] into active generators and supersession events based on their
     * [Generator.supersedes] declarations.
     *
     * ### Contract
     *
     * - **Active status**: a candidate is active if no *active* candidate lists its id in
     *   [Generator.supersedes]. Because the rule is self-referential, supersedes claims made by a
     *   dropped candidate have no effect. For example, if `a` supersedes `b` and `b` supersedes
     *   `c`, then `b` is dropped by `a` and its claim on `c` is moot — `c` remains active.
     * - **Unknown targets**: ids in `supersedes()` that do not match any candidate are silently
     *   ignored (no error, no event). Generators can safely declare supersession over ids that
     *   may not be present.
     * - **Multiple superseders**: if a candidate is superseded by several active candidates, one
     *   [Superseded] record is emitted per superseding pair.
     * - **Order preservation**: iteration order of [candidates] is preserved in [Result.active]
     *   and [Result.superseded]. Callers wanting stable output should pass an ordered set.
     *
     * @param candidates generators to resolve; must already have been filtered for classpath
     *                   availability and user-disable configuration.
     * @return the partition into surviving generators and supersession events.
     * @throws IllegalStateException if the supersedes graph among [candidates] contains a cycle.
     */
    fun resolve(candidates: Set<Generator>): Result {
        val byId = candidates.associateBy { it.generatorId() }

        val superseders = mutableMapOf<String, MutableSet<String>>()
        for (g in candidates) {
            for (target in g.supersedes().filterNotNull()) {
                if (target in byId) {
                    superseders.getOrPut(target) { linkedSetOf() }.add(g.generatorId())
                }
            }
        }

        val isActive = mutableMapOf<String, Boolean>()
        val visiting = linkedSetOf<String>()
        for (g in candidates) {
            computeActive(g.generatorId(), superseders, isActive, visiting)
        }

        val active = linkedSetOf<Generator>()
        val superseded = linkedSetOf<Superseded>()
        for (g in candidates) {
            if (isActive[g.generatorId()] == true) {
                active.add(g)
            } else {
                for (supersederId in superseders[g.generatorId()].orEmpty()) {
                    if (isActive[supersederId] == true) {
                        superseded.add(Superseded(g.generatorId(), supersederId))
                    }
                }
            }
        }
        return Result(active, superseded)
    }

    private fun computeActive(
        id: String,
        superseders: Map<String, Set<String>>,
        isActive: MutableMap<String, Boolean>,
        visiting: MutableSet<String>
    ): Boolean {
        isActive[id]?.let { return it }
        if (!visiting.add(id)) {
            throw IllegalStateException(
                "Cycle detected in generator supersedes graph involving '$id'; visiting: $visiting"
            )
        }
        try {
            for (supersederId in superseders[id].orEmpty()) {
                if (computeActive(supersederId, superseders, isActive, visiting)) {
                    isActive[id] = false
                    return false
                }
            }
            isActive[id] = true
            return true
        } finally {
            visiting.remove(id)
        }
    }
}
