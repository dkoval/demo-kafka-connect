import org.jetbrains.kotlin.gradle.utils.extendsFrom

plugins {
    kotlin("jvm") version "2.0.20"
    distribution
}

group = "com.github.dkoval"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Supported Versions and Interoperability for Confluent Platform
    // https://docs.confluent.io/platform/current/installation/versions-interoperability.html#cp-and-apache-ak-compatibility
    compileOnly("org.apache.kafka:connect-transforms:3.8.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.23.1")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}

kotlin {
    jvmToolchain(17)
}

distributions {
    main {
        contents {
            from(tasks.jar)
            from(configurations.runtimeClasspath)
        }
    }
}

configurations {
    testImplementation.extendsFrom(compileOnly)
}

tasks {
    test {
        useJUnitPlatform()
    }

    wrapper {
        gradleVersion = "8.8"
    }
}