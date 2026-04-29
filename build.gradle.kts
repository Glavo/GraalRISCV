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
val graalVmVersion = "25.0.3"
val polyglotResourceCacheDirectory = layout.buildDirectory.dir("polyglot-resource-cache")
val polyglotResourceCacheJvmArg =
    "-Dpolyglot.engine.userResourceCache=${polyglotResourceCacheDirectory.get().asFile.absolutePath}"
val unsafeModuleArgs = listOf(
    "--enable-native-access=ALL-UNNAMED",
    "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
)

application {
    applicationName = "graalriscv"
    mainClass = "org.glavo.riscv.Main"

    applicationDefaultJvmArgs = unsafeModuleArgs + listOf(
        "--sun-misc-unsafe-memory-access=allow",
        polyglotResourceCacheJvmArg
    )
}

tasks.shadowJar {
    manifest.attributes(
        "Main-Class" to mainClassName,
        "Enable-Native-Access" to "ALL-UNNAMED",
        "Add-Opens" to "java.base/jdk.internal.misc"
    )
}

dependencies {
    implementation("org.graalvm.truffle:truffle-api:$graalVmVersion")
    implementation("net.fornwall:jelf:0.11.0")
    runtimeOnly("org.graalvm.truffle:truffle-runtime:$graalVmVersion")

    compileOnly("org.jetbrains:annotations:26.1.0")
    testCompileOnly("org.jetbrains:annotations:26.1.0")

    annotationProcessor("org.graalvm.truffle:truffle-dsl-processor:$graalVmVersion")
    testAnnotationProcessor("org.graalvm.truffle:truffle-dsl-processor:$graalVmVersion")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.graalvm.polyglot:polyglot:$graalVmVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(unsafeModuleArgs)
    timeout.set(Duration.ofMinutes(10))
    systemProperty("polyglot.engine.userResourceCache", polyglotResourceCacheDirectory.get().asFile.absolutePath)
    systemProperty("java.io.tmpdir", layout.buildDirectory.dir("tmp/test-temp").get().asFile.absolutePath)
    doFirst {
        polyglotResourceCacheDirectory.get().asFile.mkdirs()
        layout.buildDirectory.dir("tmp/test-temp").get().asFile.mkdirs()
    }
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs(unsafeModuleArgs)
    doFirst {
        polyglotResourceCacheDirectory.get().asFile.mkdirs()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED")
}

apply(from = "gradle/hello-world-example.gradle.kts")
apply(from = "gradle/packaging-smoke.gradle.kts")
apply(from = "gradle/native-image.gradle.kts")
