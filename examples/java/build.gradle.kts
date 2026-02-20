plugins {
    java
    `java-library`
}

group = "lazyval"
version = "1.0.0-SNAPSHOT"

// tag::docu[]
// tag::excluded[]
repositories {
    mavenCentral()
    mavenLocal() // For local SNAPSHOT dependencies
}
val versionLazyval = project.findProperty("version.lazyval") as String? ?: "0.1.0"
// end::excluded[]
dependencies {
    compileOnly("de.qualityminds.lazyval:lazyval:$versionLazyval")
    implementation("org.mapstruct:mapstruct:1.6.3")
    compileOnly("jakarta.platform:jakarta.jakartaee-api:11.0.0")

    annotationProcessor("de.qualityminds.lazyval:lazyval-processor:$versionLazyval")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
}

tasks.withType<JavaCompile>().configureEach {
    // tag::excluded[]
    options.release.set(17)
    // end::excluded[]
    options.compilerArgs.apply{
        add("-Amapstruct.unmappedTargetPolicy=ERROR")
        add("-Alazyval.jpa.generatedPackage=test.boundary.persistence")
        add("-Alazyval.mapstruct.generatedPackage=test")
    }
}
// end::docu[]

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}