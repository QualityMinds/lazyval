
plugins {
    kotlin("jvm") version "2.2.20"
    id("com.google.devtools.ksp") version "2.2.20-2.0.3"
}

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
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.20")
    compileOnly("de.qualityminds.lazyval:lazyval:0.1.0-SNAPSHOT")
    compileOnly("de.qualityminds.lazyval:lazyval-ksp:0.1.0-SNAPSHOT") // 1.
    compileOnly("jakarta.platform:jakarta.jakartaee-api:11.0.0")

    ksp("de.qualityminds.lazyval:lazyval-ksp:0.1.0-SNAPSHOT")
}

ksp {
    arg("lazyval.jpa.generatedPackage", "test.boundary.persistence")
    arg("lazyval.mapstruct.generatedPackage", "test")
}

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