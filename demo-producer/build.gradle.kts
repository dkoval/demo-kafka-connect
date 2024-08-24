plugins {
    kotlin("jvm") version "2.0.20"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Kafka
    implementation("org.apache.kafka:kafka-clients:3.8.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.23.1")
}