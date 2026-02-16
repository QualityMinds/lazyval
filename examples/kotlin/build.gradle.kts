
plugins {
    kotlin("jvm") version "2.2.20"
    id("com.google.devtools.ksp") version "2.2.20-2.0.3"
}

// tag::docu[]
// tag::excluded[]
repositories {
    mavenCentral()
    mavenLocal() // For local SNAPSHOT dependencies
}
// end::excluded[]

val versionLazyval = project.findProperty("version.lazyval") as String? ?: "0.1.0"
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.20")
    compileOnly("de.qualityminds.lazyval:lazyval:$versionLazyval")
    implementation("org.mapstruct:mapstruct:1.6.3")
    compileOnly("jakarta.platform:jakarta.jakartaee-api:11.0.0")

    ksp("de.qualityminds.lazyval:lazyval-ksp:$versionLazyval")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
}

ksp {
    arg("lazyval.jpa.generatedPackage", "test.boundary.persistence")
    arg("lazyval.mapstruct.generatedPackage", "test")
}
// end::docu[]

kotlin {
    sourceSets {
        main {
            kotlin.srcDir("src/main/kotlin")
        }
        test {
            kotlin.srcDir("src/test/kotlin")
        }
    }
    jvmToolchain(17)
}