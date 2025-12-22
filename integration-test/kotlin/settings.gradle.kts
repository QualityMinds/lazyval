pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "integration-tests-kotlin"
include("shared")
include("spring-app")