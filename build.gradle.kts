import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Duration
import org.gradle.jvm.tasks.Jar
import org.glavo.ZigUtils

plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "9.4.1"
    id("de.undercouch.download") version "5.7.0"
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

val isWindowsHost = System.getProperty("os.name").lowercase().contains("win")
val helloWorldExampleElf = layout.buildDirectory.file("examples/hello/hello.elf")
val zigArchiveName = ZigUtils.getZigArchiveName()
val zigArchiveFile = layout.buildDirectory.file("downloads/zig/$zigArchiveName")
val zigInstallDirectory = layout.buildDirectory.dir("tools/zig")
val zigExecutable = zigInstallDirectory.map {
    it.file("${ZigUtils.getZigArchiveBaseName()}/${ZigUtils.getZigExecutableName()}")
}
val zigLocalCacheDirectory = layout.buildDirectory.dir("zig-local-cache")
val zigGlobalCacheDirectory = layout.buildDirectory.dir("zig-global-cache")
val shadowJarFile = tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }
val javaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
}
val helloWorldZigCcArguments = listOf(
    "cc",
    "--target=riscv64-freestanding",
    "-Xclang",
    "-target-feature",
    "-Xclang",
    "+m",
    "-Xclang",
    "-target-feature",
    "-Xclang",
    "+a",
    "-Xclang",
    "-target-feature",
    "-Xclang",
    "+c",
    "-mabi=lp64",
    "-mcmodel=medany",
    "-nostdlib",
    "-ffreestanding",
    "-fno-sanitize=undefined",
    "-fno-builtin",
    "-fno-pic",
    "-fno-pie",
    "-fno-stack-protector",
    "-fno-asynchronous-unwind-tables",
    "-Wl,-T,${layout.projectDirectory.file("examples/hello/linker.ld").asFile.absolutePath}",
    "-Wl,--build-id=none",
    "-o",
    helloWorldExampleElf.get().asFile.absolutePath,
    layout.projectDirectory.file("examples/hello/HelloWorld.c").asFile.absolutePath
)

val downloadZig by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    group = "build setup"
    description = "Downloads Zig ${ZigUtils.ZIG_VERSION} for the current host platform."

    src("https://ziglang.org/download/${ZigUtils.ZIG_VERSION}/${ZigUtils.getZigArchiveName()}")
    dest(zigArchiveFile.get().asFile)
    overwrite(false)
    tempAndMove(true)

    doFirst {
        val archive = zigArchiveFile.get().asFile
        if (archive.isFile && archive.length() == 0L) {
            delete(archive)
        }
    }
}

val extractZig by tasks.registering {
    group = "build setup"
    description = "Extracts the downloaded Zig toolchain."

    dependsOn(downloadZig)
    inputs.file(zigArchiveFile)
    outputs.file(zigExecutable)

    doLast {
        val installDirectory = zigInstallDirectory.get().asFile
        delete(installDirectory)
        val archive = zigArchiveFile.get().asFile
        ZigUtils.extractZigArchive(archive, installDirectory)
    }
}

tasks.register<Exec>("buildHelloWorldExample") {
    group = "verification"
    description = "Builds examples/hello/HelloWorld.c for RISC-V with the Gradle-managed Zig toolchain."

    dependsOn(extractZig)
    inputs.files("examples/hello/HelloWorld.c", "examples/hello/linker.ld")
    inputs.file(zigExecutable)
    inputs.property("zigCcArguments", helloWorldZigCcArguments)
    outputs.file(helloWorldExampleElf)

    doFirst {
        helloWorldExampleElf.get().asFile.parentFile.mkdirs()
        zigLocalCacheDirectory.get().asFile.mkdirs()
        zigGlobalCacheDirectory.get().asFile.mkdirs()
        environment("ZIG_LOCAL_CACHE_DIR", zigLocalCacheDirectory.get().asFile.absolutePath)
        environment("ZIG_GLOBAL_CACHE_DIR", zigGlobalCacheDirectory.get().asFile.absolutePath)
        executable = zigExecutable.get().asFile.absolutePath
        args(helloWorldZigCcArguments)
    }
}

tasks.register<JavaExec>("testHelloWorldExample") {
    group = "verification"
    description = "Compiles Hello World and verifies the GraalRISCV CLI output."

    dependsOn("classes", "buildHelloWorldExample")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassName
    jvmArgs(application.applicationDefaultJvmArgs)

    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    standardOutput = stdout
    errorOutput = stderr

    doFirst {
        stdout.reset()
        stderr.reset()
        setArgs(listOf(helloWorldExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (actualOutput != "Hello World!\n") {
            throw GradleException("Unexpected Hello World output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Hello World wrote to stderr: $actualError")
        }
    }
}

tasks.register("buildZigHelloWorldExample") {
    group = "verification"
    description = "Alias for buildHelloWorldExample."

    dependsOn("buildHelloWorldExample")
}

tasks.register("testZigHelloWorldExample") {
    group = "verification"
    description = "Alias for testHelloWorldExample."

    dependsOn("testHelloWorldExample")
}

tasks.register<JavaExec>("runHelloWorldExample") {
    group = "verification"
    description = "Runs the compiled Hello World RISC-V example with the GraalRISCV CLI."

    dependsOn("classes", "buildHelloWorldExample")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassName
    jvmArgs(application.applicationDefaultJvmArgs)

    doFirst {
        args(helloWorldExampleElf.get().asFile.absolutePath)
    }
}

tasks.register<Exec>("runHelloWorldInstalledExample") {
    group = "verification"
    description = "Runs the Hello World example through the installDist launch script."

    dependsOn("installDist", "buildHelloWorldExample")

    doFirst {
        val scriptName = if (isWindowsHost) {
            "graalriscv.bat"
        } else {
            "graalriscv"
        }
        val script = layout.buildDirectory.file("install/graalriscv/bin/$scriptName").get().asFile
        val elf = helloWorldExampleElf.get().asFile

        if (isWindowsHost) {
            commandLine("cmd", "/c", script.absolutePath, elf.absolutePath)
        } else {
            commandLine(script.absolutePath, elf.absolutePath)
        }
    }
}

tasks.register<Exec>("runHelloWorldShadowJarExample") {
    group = "verification"
    description = "Runs the Hello World example through the packaged Shadow JAR."

    dependsOn("shadowJar", "buildHelloWorldExample")
    inputs.file(shadowJarFile)

    doFirst {
        commandLine(
            javaLauncher.get().executablePath.asFile.absolutePath,
            "--enable-native-access=ALL-UNNAMED",
            "--sun-misc-unsafe-memory-access=allow",
            "-jar",
            shadowJarFile.get().asFile.absolutePath,
            helloWorldExampleElf.get().asFile.absolutePath
        )
    }
}

tasks.register("checkHelloWorldExample") {
    group = "verification"
    description = "Runs every available Hello World example smoke check."

    dependsOn(
        "testHelloWorldExample",
        "runHelloWorldExample",
        "runHelloWorldInstalledExample",
        "runHelloWorldShadowJarExample"
    )
}

tasks.named("check") {
    dependsOn("testHelloWorldExample")
}
