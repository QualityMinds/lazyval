plugins {
    java
    `java-library`
}

group = "lazyval"
version = "1.0.0-SNAPSHOT"

// tag::docu[]
repositories {
    mavenCentral()
    // tag::excluded[]
    mavenLocal() // For local SNAPSHOT dependencies
    // end::excluded[]
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/qualityminds/*")
    }
}


dependencies {
    compileOnly("de.qualityminds.lazyval:lazyval:0.1.0-SNAPSHOT")
    implementation("org.mapstruct:mapstruct:1.6.3")
    compileOnly("jakarta.platform:jakarta.jakartaee-api:11.0.0")

    // Annotation processors
    annotationProcessor("de.qualityminds.lazyval:lazyval-processor:0.1.0-SNAPSHOT")
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