import java.io.ByteArrayOutputStream
import java.io.File
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

fun findExecutable(command: String): String? {
    val direct = file(command)
    if (direct.isFile) {
        return direct.absolutePath
    }

    val path = System.getenv("PATH") ?: return null
    val extensions = if (isWindowsHost) {
        listOf("") + (System.getenv("PATHEXT") ?: ".EXE;.BAT;.CMD")
            .split(';')
            .filter { it.isNotBlank() }
    } else {
        listOf("")
    }

    return path.split(File.pathSeparator)
        .asSequence()
        .flatMap { directory ->
            extensions.asSequence().map { extension ->
                val fileName = if (extension.isNotEmpty() && command.endsWith(extension, ignoreCase = true)) {
                    command
                } else {
                    command + extension
                }
                File(directory, fileName)
            }
        }
        .firstOrNull { it.isFile }
        ?.absolutePath
}

val isWindowsHost = System.getProperty("os.name").lowercase().contains("win")
val riscvGcc = providers.gradleProperty("riscvGcc").orElse("riscv64-unknown-elf-gcc")
val helloWorldExampleElf = layout.buildDirectory.file("examples/hello/hello.elf")
val zigArchiveName = ZigUtils.getZigArchiveName()
val zigArchiveFile = layout.buildDirectory.file("downloads/zig/$zigArchiveName")
val zigInstallDirectory = layout.buildDirectory.dir("tools/zig")
val zigExecutable = zigInstallDirectory.map {
    it.file("${ZigUtils.getZigArchiveBaseName()}/${ZigUtils.getZigExecutableName()}")
}
val zigHelloWorldExampleElf = layout.buildDirectory.file("examples/hello/hello-zig.elf")
val shadowJarFile = tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }
val javaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
}

fun helloWorldExampleElfExists(taskName: String): Boolean {
    val elf = helloWorldExampleElf.get().asFile
    if (!elf.isFile) {
        logger.lifecycle("Skipping $taskName: ${elf.absolutePath} does not exist.")
    }
    return elf.isFile
}

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
    description = "Builds examples/hello/HelloWorld.c with a RISC-V bare-metal GCC toolchain when available."

    inputs.files("examples/hello/HelloWorld.c", "examples/hello/linker.ld")
    outputs.file(helloWorldExampleElf)

    onlyIf {
        val compiler = findExecutable(riscvGcc.get())
        if (compiler == null) {
            logger.lifecycle("Skipping buildHelloWorldExample: ${riscvGcc.get()} was not found in PATH. Use -PriscvGcc=<path> to set the compiler.")
        }
        compiler != null
    }

    doFirst {
        val compiler = findExecutable(riscvGcc.get())
            ?: throw GradleException("RISC-V GCC was not found: ${riscvGcc.get()}")
        helloWorldExampleElf.get().asFile.parentFile.mkdirs()
        executable = compiler
        args(
            "-march=rv64imac",
            "-mabi=lp64",
            "-mcmodel=medany",
            "-nostdlib",
            "-nostartfiles",
            "-ffreestanding",
            "-fno-builtin",
            "-fno-pic",
            "-fno-pie",
            "-fno-stack-protector",
            "-fno-asynchronous-unwind-tables",
            "-Wl,-T,${layout.projectDirectory.file("examples/hello/linker.ld").asFile.absolutePath}",
            "-Wl,--no-relax",
            "-Wl,--build-id=none",
            "-o",
            helloWorldExampleElf.get().asFile.absolutePath,
            layout.projectDirectory.file("examples/hello/HelloWorld.c").asFile.absolutePath
        )
    }
}

tasks.register<Exec>("buildZigHelloWorldExample") {
    group = "verification"
    description = "Builds examples/hello/HelloWorld.c for RISC-V with the Gradle-managed Zig toolchain."

    dependsOn(extractZig)
    inputs.files("examples/hello/HelloWorld.c", "examples/hello/linker.ld")
    outputs.file(zigHelloWorldExampleElf)

    doFirst {
        zigHelloWorldExampleElf.get().asFile.parentFile.mkdirs()
        executable = zigExecutable.get().asFile.absolutePath
        args(
            "cc",
            "-target",
            "riscv64-freestanding",
            "-march=rv64imac",
            "-mabi=lp64",
            "-mcmodel=medany",
            "-nostdlib",
            "-nostartfiles",
            "-ffreestanding",
            "-fno-builtin",
            "-fno-pic",
            "-fno-pie",
            "-fno-stack-protector",
            "-fno-asynchronous-unwind-tables",
            "-Wl,-T,${layout.projectDirectory.file("examples/hello/linker.ld").asFile.absolutePath}",
            "-Wl,--no-relax",
            "-Wl,--build-id=none",
            "-o",
            zigHelloWorldExampleElf.get().asFile.absolutePath,
            layout.projectDirectory.file("examples/hello/HelloWorld.c").asFile.absolutePath
        )
    }
}

tasks.register<JavaExec>("testZigHelloWorldExample") {
    group = "verification"
    description = "Compiles Hello World with Zig and verifies the GraalRISCV CLI output."

    dependsOn("classes", "buildZigHelloWorldExample")
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
        setArgs(listOf(zigHelloWorldExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (actualOutput != "Hello World!\n") {
            throw GradleException("Unexpected Zig Hello World output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Zig Hello World wrote to stderr: $actualError")
        }
    }
}

tasks.register<JavaExec>("runHelloWorldExample") {
    group = "verification"
    description = "Runs the compiled Hello World RISC-V example with the GraalRISCV CLI."

    dependsOn("classes", "buildHelloWorldExample")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassName
    jvmArgs(application.applicationDefaultJvmArgs)

    onlyIf {
        helloWorldExampleElfExists(name)
    }

    doFirst {
        args(helloWorldExampleElf.get().asFile.absolutePath)
    }
}

tasks.register<Exec>("runHelloWorldInstalledExample") {
    group = "verification"
    description = "Runs the Hello World example through the installDist launch script."

    dependsOn("installDist", "buildHelloWorldExample")

    onlyIf {
        helloWorldExampleElfExists(name)
    }

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

    onlyIf {
        helloWorldExampleElfExists(name)
    }

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
        "testZigHelloWorldExample",
        "runHelloWorldExample",
        "runHelloWorldInstalledExample",
        "runHelloWorldShadowJarExample"
    )
}

tasks.named("check") {
    dependsOn("testZigHelloWorldExample")
}
