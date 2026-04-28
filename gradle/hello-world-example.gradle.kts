import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.provider.Provider
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
val hotLoopExampleElf = layout.buildDirectory.file("examples/hello/hot-loop.elf")
val linuxStaticPrintfExampleElf = layout.buildDirectory.file("examples/linux-static/printf-hello.elf")
val linuxStaticArgvExampleElf = layout.buildDirectory.file("examples/linux-static/argv-echo.elf")
val linuxStaticFileIoExampleElf = layout.buildDirectory.file("examples/linux-static/file-io.elf")
val linuxStaticDirectoryListExampleElf = layout.buildDirectory.file("examples/linux-static/directory-list.elf")
val linuxStaticFileMutationExampleElf = layout.buildDirectory.file("examples/linux-static/file-mutation.elf")
val linuxStaticWorkingDirectoryExampleElf = layout.buildDirectory.file("examples/linux-static/working-directory.elf")
val linuxStaticFileIoRoot = layout.buildDirectory.dir("tmp/linux-static-file-io-root")
val linuxStaticDirectoryListRoot = layout.buildDirectory.dir("tmp/linux-static-directory-list-root")
val linuxStaticFileMutationRoot = layout.buildDirectory.dir("tmp/linux-static-file-mutation-root")
val linuxStaticWorkingDirectoryRoot = layout.buildDirectory.dir("tmp/linux-static-working-directory-root")
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

fun RiscVZigCcTask.configureLinuxStaticExample(sourceFileName: String, elfFile: Provider<RegularFile>) {
    dependsOn(extractZig)
    zigExecutable.set(zigExecutableFile)
    sourceFile.set(layout.projectDirectory.file("examples/linux-static/$sourceFileName"))
    outputFile.set(elfFile)
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

tasks.register<RiscVZigCcTask>("buildHotLoopExample") {
    group = "verification"
    description = "Builds examples/hello/HotLoop.c for RISC-V with the Gradle-managed Zig toolchain."

    dependsOn(extractZig)
    zigExecutable.set(zigExecutableFile)
    sourceFile.set(layout.projectDirectory.file("examples/hello/HotLoop.c"))
    linkerScript.set(layout.projectDirectory.file("examples/hello/linker.ld"))
    outputFile.set(hotLoopExampleElf)
    localCacheDirectory.set(layout.buildDirectory.dir("zig-local-cache"))
    globalCacheDirectory.set(layout.buildDirectory.dir("zig-global-cache"))
    additionalCompilerArguments.set(listOf("-O2", "-g0", "-DHOT_LOOP_ITERATIONS=1000000UL"))
}

tasks.register<RiscVZigCcTask>("buildLinuxStaticPrintfExample") {
    group = "verification"
    description = "Builds examples/linux-static/PrintfHelloWorld.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("PrintfHelloWorld.c", linuxStaticPrintfExampleElf)
}

tasks.register<RiscVZigCcTask>("buildLinuxStaticArgvExample") {
    group = "verification"
    description = "Builds examples/linux-static/ArgvEcho.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("ArgvEcho.c", linuxStaticArgvExampleElf)
}

tasks.register<RiscVZigCcTask>("buildLinuxStaticFileIoExample") {
    group = "verification"
    description = "Builds examples/linux-static/FileIo.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("FileIo.c", linuxStaticFileIoExampleElf)
}

tasks.register<RiscVZigCcTask>("buildLinuxStaticDirectoryListExample") {
    group = "verification"
    description = "Builds examples/linux-static/DirectoryList.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("DirectoryList.c", linuxStaticDirectoryListExampleElf)
}

tasks.register<RiscVZigCcTask>("buildLinuxStaticFileMutationExample") {
    group = "verification"
    description = "Builds examples/linux-static/FileMutation.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("FileMutation.c", linuxStaticFileMutationExampleElf)
}

tasks.register<RiscVZigCcTask>("buildLinuxStaticWorkingDirectoryExample") {
    group = "verification"
    description = "Builds examples/linux-static/WorkingDirectory.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("WorkingDirectory.c", linuxStaticWorkingDirectoryExampleElf)
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

tasks.register<JavaExec>("testLinuxStaticArgvExample") {
    group = "verification"
    description = "Compiles a static musl argv example and verifies the GraalRISCV CLI output."

    dependsOn("classes", "buildLinuxStaticArgvExample")
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
        setArgs(listOf(linuxStaticArgvExampleElf.get().asFile.absolutePath, "alpha", "--beta"))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        val expectedOutput = "argc=3\nargv1=alpha\nargv2=--beta\n"
        if (actualOutput != expectedOutput) {
            throw GradleException("Unexpected static argv output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Static argv example wrote to stderr: $actualError")
        }
    }
}

tasks.register<JavaExec>("testLinuxStaticFileIoExample") {
    group = "verification"
    description = "Compiles a static musl file I/O example and verifies sandboxed host-root output."

    dependsOn("classes", "buildLinuxStaticFileIoExample")
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

        val root = linuxStaticFileIoRoot.get().asFile
        delete(root)
        if (!root.mkdirs()) {
            throw GradleException("Failed to create example host root: $root")
        }

        setArgs(listOf("--host-root", root.absolutePath, linuxStaticFileIoExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (actualOutput != "file-data\n") {
            throw GradleException("Unexpected static file I/O output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Static file I/O example wrote to stderr: $actualError")
        }

        val outputFile = linuxStaticFileIoRoot.get().file("output.txt").asFile
        val fileOutput = outputFile.readText(StandardCharsets.UTF_8)
        if (fileOutput != "file-data\n") {
            throw GradleException("Unexpected static file I/O host output: ${fileOutput.trim()}")
        }
    }
}

tasks.register<JavaExec>("testLinuxStaticDirectoryListExample") {
    group = "verification"
    description = "Compiles a static musl directory listing example and verifies getdents64-backed output."

    dependsOn("classes", "buildLinuxStaticDirectoryListExample")
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

        val root = linuxStaticDirectoryListRoot.get().asFile
        delete(root)
        val entries = root.resolve("entries")
        if (!entries.mkdirs()) {
            throw GradleException("Failed to create example directory: $entries")
        }
        entries.resolve("alpha.txt").writeText("alpha\n", StandardCharsets.UTF_8)
        if (!entries.resolve("nested").mkdirs()) {
            throw GradleException("Failed to create nested example directory.")
        }

        setArgs(listOf("--host-root", root.absolutePath, linuxStaticDirectoryListExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        val expectedOutput = "alpha.txt:file\nnested:dir\n"
        if (actualOutput != expectedOutput) {
            throw GradleException("Unexpected static directory listing output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Static directory listing example wrote to stderr: $actualError")
        }
    }
}

tasks.register<JavaExec>("testLinuxStaticFileMutationExample") {
    group = "verification"
    description = "Compiles a static musl file mutation example and verifies sandboxed host-root changes."

    dependsOn("classes", "buildLinuxStaticFileMutationExample")
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

        val root = linuxStaticFileMutationRoot.get().asFile
        delete(root)
        if (!root.mkdirs()) {
            throw GradleException("Failed to create example host root: $root")
        }

        setArgs(listOf("--host-root", root.absolutePath, linuxStaticFileMutationExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (actualOutput != "mutations-ok\n") {
            throw GradleException("Unexpected static file mutation output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Static file mutation example wrote to stderr: $actualError")
        }

        val workDirectory = linuxStaticFileMutationRoot.get().file("work").asFile
        if (workDirectory.exists()) {
            throw GradleException("Static file mutation example left work directory behind: $workDirectory")
        }
    }
}

tasks.register<JavaExec>("testLinuxStaticWorkingDirectoryExample") {
    group = "verification"
    description = "Compiles a static musl working-directory example and verifies chdir/fchdir behavior."

    dependsOn("classes", "buildLinuxStaticWorkingDirectoryExample")
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

        val root = linuxStaticWorkingDirectoryRoot.get().asFile
        delete(root)
        if (!root.mkdirs()) {
            throw GradleException("Failed to create example host root: $root")
        }

        setArgs(listOf("--host-root", root.absolutePath, linuxStaticWorkingDirectoryExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (actualOutput != "cwd-ok\n") {
            throw GradleException("Unexpected static working-directory output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Static working-directory example wrote to stderr: $actualError")
        }

        val outputFile = linuxStaticWorkingDirectoryRoot.get().file("work/message.txt").asFile
        val fileOutput = outputFile.readText(StandardCharsets.UTF_8)
        if (fileOutput != "cwd-data\n") {
            throw GradleException("Unexpected static working-directory host output: ${fileOutput.trim()}")
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

tasks.register<JavaExec>("testHotLoopExample") {
    group = "verification"
    description = "Runs the freestanding hot-loop example and verifies its checksum shape."

    dependsOn("classes", "buildHotLoopExample")
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
        setArgs(listOf(hotLoopExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (!Regex("checksum=0x[0-9a-f]{16}\\n").matches(actualOutput)) {
            throw GradleException("Unexpected hot-loop output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Hot-loop example wrote to stderr: $actualError")
        }
    }
}

tasks.register<JavaExec>("runHotLoopExample") {
    group = "verification"
    description = "Runs the freestanding hot-loop example with the GraalRISCV CLI."

    dependsOn("classes", "buildHotLoopExample")
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass = mainClassName
    jvmArgs(applicationDefaultJvmArgs)

    doFirst {
        setArgs(listOf(hotLoopExampleElf.get().asFile.absolutePath))
    }
}

tasks.register<JavaExec>("runHotLoopCompilationTrace") {
    group = "verification"
    description = "Runs the freestanding hot-loop example with Truffle compilation diagnostics enabled."

    dependsOn("classes", "buildHotLoopExample")
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass = mainClassName
    jvmArgs(applicationDefaultJvmArgs)

    doFirst {
        setArgs(listOf("--debug-trace-compilation", hotLoopExampleElf.get().asFile.absolutePath))
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
            listOf(javaLauncher.get().executablePath.asFile.absolutePath) +
                    applicationDefaultJvmArgs +
                    listOf(
                        "-jar",
                        shadowJarFile.get().asFile.absolutePath,
                        helloWorldExampleElf.get().asFile.absolutePath
                    )
        )
    }
}

tasks.register("checkHelloWorldExample") {
    group = "verification"
    description = "Runs every available Hello World example smoke check."

    dependsOn(
        "testHelloWorldExample",
        "testLinuxStaticPrintfExample",
        "testLinuxStaticArgvExample",
        "testLinuxStaticFileIoExample",
        "testLinuxStaticDirectoryListExample",
        "testLinuxStaticFileMutationExample",
        "testLinuxStaticWorkingDirectoryExample",
        "runHelloWorldExample",
        "runHelloWorldInstalledExample",
        "runHelloWorldShadowJarExample"
    )
}

tasks.named("check") {
    dependsOn("testHelloWorldExample")
    dependsOn("testLinuxStaticPrintfExample")
    dependsOn("testLinuxStaticArgvExample")
    dependsOn("testLinuxStaticFileIoExample")
    dependsOn("testLinuxStaticDirectoryListExample")
    dependsOn("testLinuxStaticFileMutationExample")
    dependsOn("testLinuxStaticWorkingDirectoryExample")
}
