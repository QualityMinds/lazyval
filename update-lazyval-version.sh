#!/usr/bin/env bash
#
# Updates the lazyval version in all example build files.
# Usage: ./update-lazyval-version.sh <new-version>
# Executed as part of the Release workflow
#
set -euo pipefail

VERSION="${1:?Usage: $0 <new-version>}"
PROJECT_ROOT="$(git rev-parse --show-toplevel)"

echo "Updating lazyval version to: $VERSION"

# --- Maven (pom.xml) ---
# Updates: <version.lazyval>...</version.lazyval>
find "$PROJECT_ROOT/examples" -name "pom.xml" -exec \
  sed -i "s|<version.lazyval>.*</version.lazyval>|<version.lazyval>${VERSION}</version.lazyval>|" {} +
echo "  ✔ examples/**/pom.xml"

# --- Maven 4 (pom-maven-4.xml) ---
# Updates: <version.lazyval>...</version.lazyval>
find "$PROJECT_ROOT/examples" -name "pom-maven-4.xml" -exec \
  sed -i "s|<version.lazyval>.*</version.lazyval>|<version.lazyval>${VERSION}</version.lazyval>|" {} +
echo "  ✔ examples/**/pom-maven-4.xml"

# --- Mill (build.mill) ---
# Updates: val lazyval = sys.props.getOrElse("version.lazyval", "...")
find "$PROJECT_ROOT/examples" -name "build.mill" -exec \
  sed -i "s|getOrElse(\"version.lazyval\", \".*\")|getOrElse(\"version.lazyval\", \"${VERSION}\")|" {} +
echo "  ✔ examples/**/build.mill"

# --- Gradle (build.gradle.kts) ---
# Updates: ... ?: "..."
find "$PROJECT_ROOT/examples" -name "build.gradle.kts" -exec \
  sed -i "s|System.getProperty(\"version.lazyval\") ?: \".*\"|System.getProperty(\"version.lazyval\") ?: \"${VERSION}\"|" {} +
echo "  ✔ examples/**/build.gradle.kts"

echo "Done."
