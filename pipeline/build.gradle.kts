plugins {
    kotlin("jvm") version "1.9.23"
    application
}

group = "com.yourorg"
version = "0.1.0"

repositories {
    mavenCentral()
}

val flinkVersion = "1.19.0"

dependencies {
    implementation(kotlin("stdlib"))

    // Flink
    implementation("org.apache.flink:flink-kotlin:$flinkVersion")
    implementation("org.apache.flink:flink-streaming-java:$flinkVersion")
    implementation("org.apache.flink:flink-connector-kafka:3.1.0-1.18")

    // Redis
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.12")
}

application {
    mainClass.set("com.yourorg.MainKt")
}
