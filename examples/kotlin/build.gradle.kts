plugins {
    kotlin("jvm") version "2.2.20"
    id("com.google.devtools.ksp") version "2.3.6"
}

// tag::docu[]
// tag::excluded[]
repositories {
    mavenCentral()
    mavenLocal() // For local SNAPSHOT dependencies
}

val versionLazyval = System.getProperty("version.lazyval") ?: "0.1.1"
// end::excluded[]
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.20")
    compileOnly("com.qualityminds.lazyval:lazyval:$versionLazyval")
    implementation("org.mapstruct:mapstruct:1.6.3")
    compileOnly("jakarta.platform:jakarta.jakartaee-api:11.0.0")

    ksp("com.qualityminds.lazyval:lazyval-ksp:$versionLazyval")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
}

ksp {
    arg("lazyval.jpa.generatedPackage", "test.boundary.custom")
    arg("lazyval.mapstruct.generatedPackage", "test.custom")
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