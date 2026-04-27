import java.time.Duration

plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "9.4.1"
}

group = "org.glavo"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val mainClassName = "org.glavo.riscv.Main"

application {
    applicationName = "graalriscv"
    mainClass = "org.glavo.riscv.Main"

    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow"
    )
}

tasks.shadowJar {
    manifest.attributes(
        "Main-Class" to mainClassName,
        "Enable-Native-Access" to "ALL-UNNAMED",
    )
}

dependencies {
    implementation("org.graalvm.truffle:truffle-api:25.0.2")
    implementation("net.fornwall:jelf:0.11.0")
    runtimeOnly("org.graalvm.truffle:truffle-runtime:25.0.2")

    compileOnly("org.jetbrains:annotations:26.1.0")
    testCompileOnly("org.jetbrains:annotations:26.1.0")

    annotationProcessor("org.graalvm.truffle:truffle-dsl-processor:25.0.2")
    testAnnotationProcessor("org.graalvm.truffle:truffle-dsl-processor:25.0.2")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.graalvm.polyglot:polyglot:25.0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    timeout.set(Duration.ofMinutes(10))
}

apply(from = "gradle/hello-world-example.gradle.kts")
