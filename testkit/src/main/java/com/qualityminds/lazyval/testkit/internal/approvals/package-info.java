/**
 * Toolchain-agnostic approval-evaluation logic used by the testkit. Lives in the {@code internal}
 * tree so it can be unit-tested without an annotation processor on the classpath, while the public
 * {@code Testkit} API maps the resulting outcome to a typed {@code Testresult.{Java,Kotlin}} variant.
 */
@NullMarked
package com.qualityminds.lazyval.testkit.internal.approvals;

import org.jspecify.annotations.NullMarked;
