plugins {
    kotlin("jvm") version "2.3.20"
    application
    id("com.gradleup.shadow") version "9.0.0"
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

configurations.all {
    resolutionStrategy {
        // CVE-2025-24970: Netty SslHandler native crash via malformed packets
        val nettyVersion = "4.1.118.Final"
        force("io.netty:netty-handler:$nettyVersion")
        force("io.netty:netty-codec:$nettyVersion")
        force("io.netty:netty-transport:$nettyVersion")
        force("io.netty:netty-transport-native-epoll:$nettyVersion")
        force("io.netty:netty-common:$nettyVersion")
        force("io.netty:netty-buffer:$nettyVersion")
        force("io.netty:netty-resolver:$nettyVersion")

        // GHSA-72hv-8253-57qq: Jackson async parser bypasses maxNumberLength → DoS
        val jacksonVersion = "2.18.6"
        force("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
        force("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
        force("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    }

    // CVE-2025-66566: lz4-java information leak in safe decompressor
    resolutionStrategy.dependencySubstitution {
        substitute(module("org.lz4:lz4-java")).using(module("at.yawk.lz4:lz4-java:1.10.3"))
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    // Flink
    implementation("org.apache.flink:flink-streaming-java:$flinkVersion")
    implementation("org.apache.flink:flink-connector-kafka:4.0.1-2.0")

    // Redis
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")

    // CSV parsing
    implementation("org.apache.commons:commons-csv:1.10.0")

    // JSON/YAML serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.6")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.6")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.12")

    // Testing
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("org.apache.flink:flink-test-utils:$flinkVersion")
    testImplementation("org.testcontainers:testcontainers:1.19.8")
}

application {
    mainClass.set("com.rtagui.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runProducer") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rtagui.producer.BraSerieAProducerKt")
    args = listOf("${rootProject.projectDir}/../notebooks/bra_serie_a/BRA.csv")
}

tasks.register("deployFlink") {
    dependsOn("shadowJar")
    group = "application"
    description = "Upload and run the shadow JAR on the local Flink cluster"
    doLast {
        val flinkUrl = "http://localhost:8081"
        val jar = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").get().archiveFile.get().asFile

        // Upload JAR
        val uploadOutput = providers.exec {
            commandLine("curl", "-sf", "-X", "POST", "-F", "jarfile=@${jar.absolutePath}", "$flinkUrl/jars/upload")
        }.standardOutput.asText.get()

        val jarId = groovy.json.JsonSlurper().parseText(uploadOutput)
            .let { (it as Map<*, *>)["filename"] as String }
            .substringAfterLast("/")

        // Run JAR
        providers.exec {
            commandLine("curl", "-sf", "-X", "POST", "$flinkUrl/jars/$jarId/run")
        }.standardOutput.asText.get().also { println("Flink response: $it") }

        println("Job submitted successfully. Check $flinkUrl for status.")
    }
}
