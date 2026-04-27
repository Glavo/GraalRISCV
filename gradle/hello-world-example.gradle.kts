import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.glavo.RiscVZigCcTask
import org.glavo.ZigUtils

val applicationExtension = extensions.getByType<JavaApplication>()
val sourceSets = extensions.getByType<SourceSetContainer>()
val javaToolchains = extensions.getByType<JavaToolchainService>()

val mainClassName = applicationExtension.mainClass.get()
val applicationDefaultJvmArgs = applicationExtension.applicationDefaultJvmArgs.toList()
val isWindowsHost = System.getProperty("os.name").lowercase().contains("win")
val helloWorldExampleElf = layout.buildDirectory.file("examples/hello/hello.elf")
val linuxStaticPrintfExampleElf = layout.buildDirectory.file("examples/linux-static/printf-hello.elf")
val zigArchiveName = ZigUtils.getZigArchiveName()
val zigArchiveFile = layout.buildDirectory.file("downloads/zig/$zigArchiveName")
val zigInstallDirectory = layout.buildDirectory.dir("tools/zig")
val zigExecutableFile = zigInstallDirectory.map {
    it.file("${ZigUtils.getZigArchiveBaseName()}/${ZigUtils.getZigExecutableName()}")
}
val shadowJarFile = tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }
val javaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
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
    outputs.file(zigExecutableFile)

    doLast {
        val installDirectory = zigInstallDirectory.get().asFile
        delete(installDirectory)
        val archive = zigArchiveFile.get().asFile
        ZigUtils.extractZigArchive(archive, installDirectory)
    }
}

tasks.register<RiscVZigCcTask>("buildHelloWorldExample") {
    group = "verification"
    description = "Builds examples/hello/HelloWorld.c for RISC-V with the Gradle-managed Zig toolchain."

    dependsOn(extractZig)
    zigExecutable.set(zigExecutableFile)
    sourceFile.set(layout.projectDirectory.file("examples/hello/HelloWorld.c"))
    linkerScript.set(layout.projectDirectory.file("examples/hello/linker.ld"))
    outputFile.set(helloWorldExampleElf)
    localCacheDirectory.set(layout.buildDirectory.dir("zig-local-cache"))
    globalCacheDirectory.set(layout.buildDirectory.dir("zig-global-cache"))
}

tasks.register<RiscVZigCcTask>("buildLinuxStaticPrintfExample") {
    group = "verification"
    description = "Builds examples/linux-static/PrintfHelloWorld.c as a static riscv64-linux-musl executable."

    dependsOn(extractZig)
    zigExecutable.set(zigExecutableFile)
    sourceFile.set(layout.projectDirectory.file("examples/linux-static/PrintfHelloWorld.c"))
    outputFile.set(linuxStaticPrintfExampleElf)
    localCacheDirectory.set(layout.buildDirectory.dir("zig-local-cache"))
    globalCacheDirectory.set(layout.buildDirectory.dir("zig-global-cache"))
    target.set("riscv64-linux-musl")
    enabledTargetFeatures.set(listOf("m", "a", "f", "d", "c", "zicsr", "zifencei"))
    abi.set("lp64d")
    codeModel.set("medany")
    freestanding.set(false)
    staticLinking.set(true)
    additionalCompilerArguments.set(listOf("-O0", "-g0", "-no-pie"))
}

tasks.register<JavaExec>("testHelloWorldExample") {
    group = "verification"
    description = "Compiles Hello World and verifies the GraalRISCV CLI output."

    dependsOn("classes", "buildHelloWorldExample")
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass = mainClassName
    jvmArgs(applicationDefaultJvmArgs)

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

tasks.register<JavaExec>("testLinuxStaticPrintfExample") {
    group = "verification"
    description = "Compiles a static musl printf Hello World and verifies the GraalRISCV CLI output."

    dependsOn("classes", "buildLinuxStaticPrintfExample")
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass = mainClassName
    jvmArgs(applicationDefaultJvmArgs)

    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    standardOutput = stdout
    errorOutput = stderr

    doFirst {
        stdout.reset()
        stderr.reset()
        setArgs(listOf(linuxStaticPrintfExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (actualOutput != "Hello World!\n") {
            throw GradleException("Unexpected static printf output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Static printf Hello World wrote to stderr: $actualError")
        }
    }
}

tasks.register<JavaExec>("runLinuxStaticPrintfExample") {
    group = "verification"
    description = "Runs the static musl printf Hello World example with the GraalRISCV CLI."

    dependsOn("classes", "buildLinuxStaticPrintfExample")
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass = mainClassName
    jvmArgs(applicationDefaultJvmArgs)

    doFirst {
        setArgs(listOf(linuxStaticPrintfExampleElf.get().asFile.absolutePath))
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
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass = mainClassName
    jvmArgs(applicationDefaultJvmArgs)

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
        "testLinuxStaticPrintfExample",
        "runHelloWorldExample",
        "runHelloWorldInstalledExample",
        "runHelloWorldShadowJarExample"
    )
}

tasks.named("check") {
    dependsOn("testHelloWorldExample")
    dependsOn("testLinuxStaticPrintfExample")
}
