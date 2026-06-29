package com.qualityminds.lazyval.testkit.dependencies;

import com.qualityminds.lazyval.collections.NonEmptySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to resolve Maven dependencies for dynamic classloading during testing.
 */
class MavenResolver {

    static final String PROP_RESOLVER_REPO = "lazyval.testkit.maven-repo";
    static final String PROP_RESOLVER_MIRROR = "lazyval.testkit.maven-mirror";
    static final String ENV_RESOLVER_MIRROR_USERNAME = "LAZYVAL_TESTKIT_MIRROR_USERNAME";
    static final String ENV_RESOLVER_MIRROR_PASSWORD = "LAZYVAL_TESTKIT_MIRROR_PASSWORD";

    private static final Logger logger = LoggerFactory.getLogger(MavenResolver.class);
    private static final String userHome = System.getProperty("user.home");
    private static final String defaultMavenRepo = userHome + "/.m2/repository";
    private static final String defaultMavenMirror = "https://repo1.maven.org/maven2/";

    private static File getLocalRepo() {
        return new File(System.getProperty(PROP_RESOLVER_REPO, defaultMavenRepo));
    }

    private static String getMavenMirror() {
        return ensureTrailingSlash(System.getProperty(PROP_RESOLVER_MIRROR, defaultMavenMirror));
    }

    private static String ensureTrailingSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    static NonEmptySet<File> resolveDependencies(String... coordinates) {
        // Throw on the first failing coordinate with the coord embedded in the message. The previous
        // behavior — log-at-WARN and continue, then let NonEmptySet.collector() throw a misleading
        // "non-empty set required" — buried the actual cause in SLF4J output the test author would
        // typically not have configured visibly.
        List<File> files = new ArrayList<>();

        for (String coord : coordinates) {
            var parts = coord.split(":", -1);
            if (parts.length < 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                throw new IllegalStateException(
                        "could not resolve '" + coord + "' — expected coordinate format is "
                                + "'groupId:artifactId:version'");
            }

            var groupId = parts[0];
            var artifactId = parts[1];
            var version = parts[2];

            var localRepo = getLocalRepo();
            var localFile = findInLocalRepository(localRepo, groupId, artifactId, version);

            if (localFile.exists() && localFile.length() > 0) {
                logger.debug("Found in local Maven repo: {} ({} bytes)", localFile.getName(), localFile.length());
                files.add(localFile);
            } else {
                files.add(downloadToLocalRepository(localRepo, coord, groupId, artifactId, version));
            }
        }

        return NonEmptySet.ofAll(files);
    }

    private static File findInLocalRepository(File localRepo, String groupId, String artifactId, String version) {
        var groupPath = groupId.replace('.', File.separatorChar);
        var artifactPath = groupPath + File.separator + artifactId + File.separator +
                version + File.separator + artifactId + "-" + version + ".jar";

        return new File(localRepo, artifactPath);
    }

    private static File downloadToLocalRepository(File localRepo, String coord,
                                                  String groupId, String artifactId, String version) {
        var groupPath = groupId.replace('.', '/');
        var jarName = artifactId + "-" + version + ".jar";
        var url = getMavenMirror() + groupPath + "/" + artifactId + "/" + version + "/" + jarName;

        var localGroupPath = groupId.replace('.', File.separatorChar);
        var localPath = localGroupPath + File.separator + artifactId + File.separator +
                version + File.separator + jarName;
        var localFile = new File(localRepo, localPath);

        try {
            //noinspection ResultOfMethodCallIgnored
            localFile.getParentFile().mkdirs();

            logger.debug("Downloading {}", url);

            // Download to temporary file first (atomic operation)
            var tempFile = new File(localFile.getParentFile(), jarName + ".tmp");

            var connection = new URL(url).openConnection();
            var username = System.getenv(ENV_RESOLVER_MIRROR_USERNAME);
            var password = System.getenv(ENV_RESOLVER_MIRROR_PASSWORD);
            if (username != null && password != null) {
                var credentials = java.util.Base64.getEncoder()
                        .encodeToString((username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                connection.setRequestProperty("Authorization", "Basic " + credentials);
            }

            try (InputStream input = connection.getInputStream();
                 OutputStream output = Files.newOutputStream(tempFile.toPath())) {

                var buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            }

            // Atomic move to final location
            if (tempFile.renameTo(localFile)) {
                logger.debug("Downloaded and cached: {} ({} bytes)", localFile.getName(), localFile.length());
                return localFile;
            } else {
                logger.debug("Failed to move temp file to final location");
                // Use temp file as fallback
                tempFile.deleteOnExit();
                return tempFile;
            }

        } catch (FileNotFoundException e) {
            throw new IllegalStateException(
                    "could not resolve " + coord + " — artifact not found at " + url, e);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "could not resolve " + coord + " — download failed: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves the compiled classes of the given module for testing purposes.
     * Is especially needed when no `mvn install` was run before, like when checking out the project and directly
     * running a test from the IDE.
     */
    static NonEmptySet<File> getModuleClasses(String relativeModulePath) {
        File mavenWorkspace = findMavenWorkspaceRoot();
        List<File> compiledClasses = new ArrayList<>();

        File moduleClassesDir = new File(mavenWorkspace, relativeModulePath + "/target/classes");
        if (moduleClassesDir.exists() && moduleClassesDir.isDirectory()) {
            compiledClasses.add(moduleClassesDir);
            logger.debug("Added module classes: " + moduleClassesDir.getAbsolutePath());
        }

        if (compiledClasses.isEmpty()) {
            throw new RuntimeException("No compiled classes found in Maven output. Make sure to run proper reactor build.");
        }

        return NonEmptySet.ofAll(compiledClasses);
    }

    private static File findMavenWorkspaceRoot() {
        File currentDir = new File(".").getAbsoluteFile();
        int depth = 0;
        while (currentDir != null && depth++ < 3) {
            var pomFile = new File(currentDir, "pom.xml");
            if (pomFile.exists() && isLazyvalProjectPom(pomFile)) {
                return currentDir;
            }
            currentDir = currentDir.getParentFile();
        }
        throw new RuntimeException("Could not find Lazyvals Maven workspace root (no pom.xml found)");
    }


    /**
     * The relative module lookup is only needed when running tests within this source tree.
     * Make sure that it is our source tree, otherwise side effects may occur.
     */
    private static boolean isLazyvalProjectPom(File pomFile) {
        try {
            String content = Files.readString(pomFile.toPath());

            // 1. Find the <parent> block using DOTALL mode to include newlines
            Matcher parentMatcher = Pattern.compile("<parent>(.*?)</parent>", Pattern.DOTALL).matcher(content);

            if (parentMatcher.find()) {
                String parentContent = parentMatcher.group(1);

                // 2. Check for GroupId and ArtifactId within that block
                // Using regex handles varying whitespace around the values
                boolean hasGroupId = Pattern.compile("<groupId>\\s*com\\.qualityminds\\.lazyval\\s*</groupId>")
                        .matcher(parentContent).find();

                boolean hasArtifactId = Pattern.compile("<artifactId>\\s*lazyval-parent\\s*</artifactId>")
                        .matcher(parentContent).find();

                // Both must be present, but version is ignored
                return hasGroupId && hasArtifactId;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}