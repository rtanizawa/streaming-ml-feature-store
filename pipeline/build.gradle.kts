plugins {
    kotlin("jvm") version "2.1.10"
    application
}

kotlin {
    jvmToolchain(21)
}

group = "com.rtagui"
version = "0.1.0"

repositories {
    mavenCentral()
}

val flinkVersion = "2.2.0"

dependencies {
    implementation(kotlin("stdlib"))

    // Flink
    implementation("org.apache.flink:flink-streaming-java:$flinkVersion")
    implementation("org.apache.flink:flink-connector-kafka:4.0.1-2.0")

    // Redis
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")

    // CSV parsing
    implementation("org.apache.commons:commons-csv:1.10.0")

    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.12")
}

application {
    mainClass.set("com.rtagui.MainKt")
}

tasks.register<JavaExec>("runProducer") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rtagui.producer.BraSerieAProducerKt")
    args = listOf("/Users/rafaeltanizawa/Downloads/BRA.csv")
}
