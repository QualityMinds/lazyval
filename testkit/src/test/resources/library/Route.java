package library;

import library.shared.Distance;

/** Compiles only if the library's {@link Distance} is on the classpath. */
public record Route(Distance length) {
}
