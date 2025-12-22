plugins {
    kotlin("jvm") version "2.2.21" apply false
    kotlin("plugin.spring") version "2.2.21" apply false
    kotlin("plugin.jpa") version "2.2.21" apply false
    kotlin("plugin.allopen") version "2.2.21" apply false
    id("org.springframework.boot") version "4.0.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.4" apply false
}
allprojects {
    repositories {
        maven {
            url = uri("${rootProject.projectDir}/target/local-repo")
        }
        mavenCentral()
    }
}