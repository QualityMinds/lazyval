package de.qualityminds.lazyval.testkit.internal;

/**
 * Internal use only.
 * Needed to resolve the version of {@code InternalModuleDependencies} during testkit execution.
 */
public interface Versions {

   /**
    * The version Lazyval was built with. Internal use only.
    */
   public static final String LAZYVAL_BUILD_VERSION = "${project.version}";

}