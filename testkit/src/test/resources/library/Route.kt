package library

import library.shared.Distance

/** Compiles only if the library's [Distance] is on the classpath. */
data class Route(val length: Distance)
