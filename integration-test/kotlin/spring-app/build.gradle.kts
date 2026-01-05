plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("plugin.allopen")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.1"))
    implementation(project(":shared"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webtestclient")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    compileOnly("de.qualityminds.lazyval:lazyval:0.1.0-SNAPSHOT")
    ksp("de.qualityminds.lazyval:lazyval-ksp:0.1.0-SNAPSHOT")
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

ksp {
    arg("lazyval.jpa.generatedPackage", "de.qualityminds.lazyval.integration")
    arg("lazyval.mapstruct.generatedPackage", "de.qualityminds.lazyval.integration")
    arg("lazyval.values", "de.qualityminds.lazyval.integration.shared.Quantity,de.qualityminds.lazyval.integration.shared.Isbn")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.apply {
        add("-Amapstruct.defaultComponentModel=spring")
        add("-Amapstruct.defaultInjectionStrategy=constructor")
        add("-Amapstruct.unmappedTargetPolicy=ERROR")
        add("-Amapstruct.generatedAnnotationType=jakarta")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}