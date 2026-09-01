package scenarios.failing;

import com.qualityminds.lazyval.LazyValue;

/**
 * The record half of the type-visibility rule. Worth its own scenario even though the check itself
 * sits above the record/class split in {@code LazyvalElementValidator#validate}: records take the
 * other branch afterwards, and their canonical constructor inherits the type's visibility, so this is
 * the shape that would report twice — once for the type, once for the constructor — if the gate in
 * {@code validateReconstruction} stopped deferring to the type rule.
 *
 * Carries a generator on the classpath so the run gets far enough to generate.
 */
@LazyValue
record PackagePrivateRecord(String value) {
}
