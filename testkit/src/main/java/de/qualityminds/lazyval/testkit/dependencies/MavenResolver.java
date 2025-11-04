package de.qualityminds.lazyval.testkit.dependencies;

import de.qualityminds.lazyval.collections.NonEmptySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to resolve Maven dependencies for dynamic classloading during testing.
 */
public class MavenResolver {

    private static final Logger logger = LoggerFactory.getLogger(MavenResolver.class);

    public static NonEmptySet<File> resolveDependencies(String... coordinates) {
        var localRepo = new File(System.getProperty("user.home"), ".m2/repository");
        List<File> files = new ArrayList<>();

        for (String coord : coordinates) {
            var parts = coord.split(":");
            if (parts.length < 3) {
                logger.error("Invalid coordinate format: {} (expected groupId:artifactId:version)", coord);
                continue;
            }

            var groupId = parts[0];
            var artifactId = parts[1];
            var version = parts[2];

            var localFile = findInLocalRepository(localRepo, groupId, artifactId, version);

            if (localFile != null && localFile.exists() && localFile.length() > 0) {
                logger.debug("Found in local Maven repo: {} ({} bytes)", localFile.getName(), localFile.length());
                files.add(localFile);
            } else {
                var downloadedFile = downloadToLocalRepository(localRepo, groupId, artifactId, version);
                if (downloadedFile != null) {
                    files.add(downloadedFile);
                }
            }
        }

        return files.stream()
                .filter(file -> file != null && file.exists() && file.length() > 0)
                .collect(NonEmptySet.collector());
    }

    private static File findInLocalRepository(File localRepo, String groupId, String artifactId, String version) {
        var groupPath = groupId.replace('.', File.separatorChar);
        var artifactPath = groupPath + File.separator + artifactId + File.separator +
                version + File.separator + artifactId + "-" + version + ".jar";

        return new File(localRepo, artifactPath);
    }

    private static File downloadToLocalRepository(File localRepo, String groupId, String artifactId, String version) {
        var groupPath = groupId.replace('.', '/');
        var jarName = artifactId + "-" + version + ".jar";
        var url = "https://repo1.maven.org/maven2/" + groupPath + "/" + artifactId + "/" + version + "/" + jarName;

        var localGroupPath = groupId.replace('.', File.separatorChar);
        var localPath = localGroupPath + File.separator + artifactId + File.separator +
                version + File.separator + jarName;
        var localFile = new File(localRepo, localPath);

        try {
            localFile.getParentFile().mkdirs();

            logger.debug("Downloading {}", url);

            // Download to temporary file first (atomic operation)
            var tempFile = new File(localFile.getParentFile(), jarName + ".tmp");

            try (InputStream input = new URL(url).openStream();
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
            logger.warn("Artifact not found: {}", url);
            return null;
        } catch (IOException e) {
            logger.warn("Download failed for {}:{}:{} - {}", groupId, artifactId, version, e.getMessage());
            return null;
        }
    }


    /**
     * Resolves the compiled classes of the core module for testing purposes.
     */
    public static NonEmptySet<File> getCoreModuleClasses() {
        File mavenWorkspace = findMavenWorkspaceRoot();
        List<File> compiledClasses = new ArrayList<>();

        File coreClassesDir = new File(mavenWorkspace, "../core/target/classes");
        if (coreClassesDir.exists() && coreClassesDir.isDirectory()) {
            compiledClasses.add(coreClassesDir);
            logger.debug("Added core module classes: " + coreClassesDir.getAbsolutePath());
        }

        if (compiledClasses.isEmpty()) {
            throw new RuntimeException("No compiled classes found in Maven output. Make sure to run proper reactor build.");
        }

        return NonEmptySet.ofAll(compiledClasses);
    }

    /**
     * Resolves the compiled classes of the annotation-processor module for testing purposes.
     */
    public static NonEmptySet<File> getModuleClasses(String relativeModulePath) {
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
        while (currentDir != null) {
            if (new File(currentDir, "pom.xml").exists()) {
                return currentDir;
            }
            currentDir = currentDir.getParentFile();
        }
        throw new RuntimeException("Could not find Maven workspace root (no pom.xml found)");
    }
}