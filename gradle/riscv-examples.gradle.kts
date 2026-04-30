import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.gradle.api.file.RelativePath
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.glavo.go.GoUtils
import org.glavo.go.RiscVGoBuildTask
import org.glavo.zig.AbstractRiscVZigCCTask
import org.glavo.zig.ExtractZigTask
import org.glavo.zig.RiscVFreestandingZigCCTask
import org.glavo.zig.RiscVLinuxMuslStaticZigCCTask
import org.glavo.zig.ZigUtils

val applicationExtension = extensions.getByType<JavaApplication>()
val sourceSets = extensions.getByType<SourceSetContainer>()
val javaToolchains = extensions.getByType<JavaToolchainService>()

val mainClassName = applicationExtension.mainClass.get()
val applicationDefaultJvmArgs = applicationExtension.applicationDefaultJvmArgs.toList()
val isWindowsHost = System.getProperty("os.name").lowercase().contains("win")
val zigArchiveName = ZigUtils.getZigArchiveName()
val zigArchiveFile = layout.buildDirectory.file("downloads/zig/$zigArchiveName")
val zigInstallDirectory = layout.buildDirectory.dir("tools/zig/${ZigUtils.getZigArchiveBaseName()}")
val zigExecutableFile = zigInstallDirectory.map {
    it.file(ZigUtils.getZigExecutableName())
}
val goArchiveName = GoUtils.getGoArchiveName()
val goArchiveFile = layout.buildDirectory.file("downloads/go/$goArchiveName")
val goInstallDirectory = layout.buildDirectory.dir("tools/go/${GoUtils.getGoDistributionName()}")
val goExecutableFile = goInstallDirectory.map {
    it.file("go/bin/${GoUtils.getGoExecutableName()}")
}
val configuredZigExecutablePath = providers.gradleProperty("graalriscv.zigExecutable")
    .orElse(providers.gradleProperty("zigExecutable"))
    .orElse(providers.environmentVariable("ZIG_EXECUTABLE"))
val configuredGoExecutablePath = providers.gradleProperty("graalriscv.goExecutable")
    .orElse(providers.gradleProperty("goExecutable"))
    .orElse(providers.environmentVariable("GO_EXECUTABLE"))
val shadowJarFile = tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }
val javaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
}

fun AbstractRiscVZigCCTask.configureZigExecutable() {
    val configuredPath = configuredZigExecutablePath.orNull
    if (configuredPath != null) {
        zigExecutable.fileValue(file(configuredPath))
    } else {
        dependsOn(extractZig)
        zigExecutable.set(zigExecutableFile)
    }
}

fun RiscVGoBuildTask.configureGoExecutable() {
    val configuredPath = configuredGoExecutablePath.orNull
    if (configuredPath != null) {
        goExecutable.fileValue(file(configuredPath))
    } else {
        dependsOn(extractGo)
        goExecutable.set(goExecutableFile)
    }
}

fun RiscVLinuxMuslStaticZigCCTask.configureLinuxStaticExample(sourceFileName: String, elfFile: Provider<RegularFile>) {
    configureZigExecutable()
    sourceFiles.from(layout.projectDirectory.file("examples/linux-static/$sourceFileName"))
    outputFile.set(elfFile)
    localCacheDirectory.set(layout.buildDirectory.dir("zig-local-cache"))
    globalCacheDirectory.set(layout.buildDirectory.dir("zig-global-cache"))
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

val downloadGo by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    group = "build setup"
    description = "Downloads Go ${GoUtils.GO_VERSION} for the current host platform."

    src("https://go.dev/dl/${GoUtils.getGoArchiveName()}")
    dest(goArchiveFile.get().asFile)
    overwrite(false)
    tempAndMove(true)
    retries(3)
    connectTimeout(30_000)
    readTimeout(30_000)

    doFirst {
        val archive = goArchiveFile.get().asFile
        if (archive.isFile && archive.length() == 0L) {
            delete(archive)
        }
    }
}

val extractGo by tasks.registering {
    group = "build setup"
    description = "Extracts the downloaded Go toolchain."

    dependsOn(downloadGo)
    inputs.file(goArchiveFile)
    outputs.file(goExecutableFile)

    doLast {
        if (goExecutableFile.get().asFile.isFile) {
            return@doLast
        }

        val installDirectory = goInstallDirectory.get().asFile
        delete(installDirectory)
        val archive = goArchiveFile.get().asFile
        GoUtils.extractGoArchive(archive, installDirectory)
    }
}

tasks.register<Exec>("goToolchainVersion") {
    group = "build setup"
    description = "Prints the managed or configured Go toolchain version."

    val configuredPath = configuredGoExecutablePath.orNull
    if (configuredPath == null) {
        dependsOn(extractGo)
    }

    doFirst {
        val executable = configuredGoExecutablePath.orNull?.let { file(it) } ?: goExecutableFile.get().asFile
        commandLine(executable.absolutePath, "version")
    }
}

val extractZig by tasks.registering(ExtractZigTask::class) {
    group = "build setup"
    description = "Extracts the downloaded Zig toolchain."

    dependsOn(downloadZig)
    archiveFile.set(zigArchiveFile)
    installDirectory.set(zigInstallDirectory)
}

// Static Go hello-world example.
val goHelloWorldExampleDirectory = layout.projectDirectory.dir("examples/go/go-hello")
val goHelloWorldExampleElf = layout.buildDirectory.file("examples/go/go-hello/hello-world")

tasks.register<RiscVGoBuildTask>("buildGoHelloWorldExample") {
    group = "verification"
    description = "Builds examples/go/go-hello as a static linux/riscv64 Go executable."

    configureGoExecutable()
    moduleDirectory.set(goHelloWorldExampleDirectory)
    outputFile.set(goHelloWorldExampleElf)
    buildCacheDirectory.set(layout.buildDirectory.dir("go-build-cache"))
    moduleCacheDirectory.set(layout.buildDirectory.dir("go-module-cache"))
}

tasks.register<JavaExec>("testGoHelloWorldExample") {
    group = "verification"
    description = "Builds and verifies the static linux/riscv64 Go hello-world example."

    dependsOn("classes", "buildGoHelloWorldExample")
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
        setArgs(listOf(goHelloWorldExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        val expectedOutput = """
            Hello and welcome, gopher!
            i = 100
            i = 50
            i = 33
            i = 25
            i = 20
        """.trimIndent() + "\n"
        if (actualOutput != expectedOutput) {
            throw GradleException("Unexpected Go hello-world output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Go hello-world example wrote to stderr: $actualError")
        }
    }
}

tasks.register<JavaExec>("runGoHelloWorldExample") {
    group = "verification"
    description = "Runs the static linux/riscv64 Go hello-world example with the GraalRISCV CLI."

    dependsOn("classes", "buildGoHelloWorldExample")
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass = mainClassName
    jvmArgs(applicationDefaultJvmArgs)

    doFirst {
        setArgs(listOf(goHelloWorldExampleElf.get().asFile.absolutePath))
    }
}

// Freestanding Hello World example.
val helloWorldExampleElf = layout.buildDirectory.file("examples/freestanding/hello.elf")

tasks.register<RiscVFreestandingZigCCTask>("buildHelloWorldExample") {
    group = "verification"
    description = "Builds examples/freestanding/HelloWorld.c for RISC-V with Zig CC."

    configureZigExecutable()
    sourceFiles.from(layout.projectDirectory.file("examples/freestanding/HelloWorld.c"))
    linkerScript.set(layout.projectDirectory.file("examples/freestanding/linker.ld"))
    outputFile.set(helloWorldExampleElf)
    localCacheDirectory.set(layout.buildDirectory.dir("zig-local-cache"))
    globalCacheDirectory.set(layout.buildDirectory.dir("zig-global-cache"))
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

// Freestanding hot-loop example.
val hotLoopExampleElf = layout.buildDirectory.file("examples/freestanding/hot-loop.elf")

tasks.register<RiscVFreestandingZigCCTask>("buildHotLoopExample") {
    group = "verification"
    description = "Builds examples/freestanding/HotLoop.c for RISC-V with Zig CC."

    configureZigExecutable()
    sourceFiles.from(layout.projectDirectory.file("examples/freestanding/HotLoop.c"))
    linkerScript.set(layout.projectDirectory.file("examples/freestanding/linker.ld"))
    outputFile.set(hotLoopExampleElf)
    localCacheDirectory.set(layout.buildDirectory.dir("zig-local-cache"))
    globalCacheDirectory.set(layout.buildDirectory.dir("zig-global-cache"))
    additionalCompilerArguments.set(listOf("-O2", "-g0", "-DHOT_LOOP_ITERATIONS=1000000UL"))
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

// Static Linux printf example.
val linuxStaticPrintfExampleElf = layout.buildDirectory.file("examples/linux-static/printf-hello.elf")

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildLinuxStaticPrintfExample") {
    group = "verification"
    description = "Builds examples/linux-static/PrintfHelloWorld.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("PrintfHelloWorld.c", linuxStaticPrintfExampleElf)
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

// Static Linux argv example.
val linuxStaticArgvExampleElf = layout.buildDirectory.file("examples/linux-static/argv-echo.elf")

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildLinuxStaticArgvExample") {
    group = "verification"
    description = "Builds examples/linux-static/ArgvEcho.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("ArgvEcho.c", linuxStaticArgvExampleElf)
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

// Static Linux file I/O example.
val linuxStaticFileIoExampleElf = layout.buildDirectory.file("examples/linux-static/file-io.elf")
val linuxStaticFileIoRoot = layout.buildDirectory.dir("tmp/linux-static-file-io-root")

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildLinuxStaticFileIoExample") {
    group = "verification"
    description = "Builds examples/linux-static/FileIo.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("FileIo.c", linuxStaticFileIoExampleElf)
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

// Static Linux directory listing example.
val linuxStaticDirectoryListExampleElf = layout.buildDirectory.file("examples/linux-static/directory-list.elf")
val linuxStaticDirectoryListRoot = layout.buildDirectory.dir("tmp/linux-static-directory-list-root")

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildLinuxStaticDirectoryListExample") {
    group = "verification"
    description = "Builds examples/linux-static/DirectoryList.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("DirectoryList.c", linuxStaticDirectoryListExampleElf)
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

// Static Linux file mutation example.
val linuxStaticFileMutationExampleElf = layout.buildDirectory.file("examples/linux-static/file-mutation.elf")
val linuxStaticFileMutationRoot = layout.buildDirectory.dir("tmp/linux-static-file-mutation-root")

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildLinuxStaticFileMutationExample") {
    group = "verification"
    description = "Builds examples/linux-static/FileMutation.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("FileMutation.c", linuxStaticFileMutationExampleElf)
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

// Static Linux working-directory example.
val linuxStaticWorkingDirectoryExampleElf = layout.buildDirectory.file("examples/linux-static/working-directory.elf")
val linuxStaticWorkingDirectoryRoot = layout.buildDirectory.dir("tmp/linux-static-working-directory-root")

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildLinuxStaticWorkingDirectoryExample") {
    group = "verification"
    description = "Builds examples/linux-static/WorkingDirectory.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("WorkingDirectory.c", linuxStaticWorkingDirectoryExampleElf)
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

// Static Linux filesystem-status example.
val linuxStaticFilesystemStatusExampleElf = layout.buildDirectory.file("examples/linux-static/filesystem-status.elf")
val linuxStaticFilesystemStatusRoot = layout.buildDirectory.dir("tmp/linux-static-filesystem-status-root")

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildLinuxStaticFilesystemStatusExample") {
    group = "verification"
    description = "Builds examples/linux-static/FilesystemStatus.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("FilesystemStatus.c", linuxStaticFilesystemStatusExampleElf)
}

tasks.register<JavaExec>("testLinuxStaticFilesystemStatusExample") {
    group = "verification"
    description = "Compiles a static musl statvfs example and verifies statfs/fstatfs behavior."

    dependsOn("classes", "buildLinuxStaticFilesystemStatusExample")
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

        val root = linuxStaticFilesystemStatusRoot.get().asFile
        delete(root)
        if (!root.mkdirs()) {
            throw GradleException("Failed to create example host root: $root")
        }

        setArgs(listOf("--host-root", root.absolutePath, linuxStaticFilesystemStatusExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (actualOutput != "statvfs-ok\n") {
            throw GradleException("Unexpected static filesystem status output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Static filesystem status example wrote to stderr: $actualError")
        }

        val outputFile = linuxStaticFilesystemStatusRoot.get().file("status.txt").asFile
        val fileOutput = outputFile.readText(StandardCharsets.UTF_8)
        if (fileOutput != "status\n") {
            throw GradleException("Unexpected static filesystem status host output: ${fileOutput.trim()}")
        }
    }
}

// Static Linux statx metadata example.
val linuxStaticStatxMetadataExampleElf = layout.buildDirectory.file("examples/linux-static/statx-metadata.elf")
val linuxStaticStatxMetadataRoot = layout.buildDirectory.dir("tmp/linux-static-statx-metadata-root")

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildLinuxStaticStatxMetadataExample") {
    group = "verification"
    description = "Builds examples/linux-static/StatxMetadata.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("StatxMetadata.c", linuxStaticStatxMetadataExampleElf)
}

tasks.register<JavaExec>("testLinuxStaticStatxMetadataExample") {
    group = "verification"
    description = "Compiles a static musl statx example and verifies sandboxed metadata output."

    dependsOn("classes", "buildLinuxStaticStatxMetadataExample")
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

        val root = linuxStaticStatxMetadataRoot.get().asFile
        delete(root)
        if (!root.mkdirs()) {
            throw GradleException("Failed to create example host root: $root")
        }

        setArgs(listOf("--host-root", root.absolutePath, linuxStaticStatxMetadataExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (actualOutput != "statx-ok\n") {
            throw GradleException("Unexpected static statx output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Static statx example wrote to stderr: $actualError")
        }

        val outputFile = linuxStaticStatxMetadataRoot.get().file("statx.txt").asFile
        val fileOutput = outputFile.readText(StandardCharsets.UTF_8)
        if (fileOutput != "statx-data\n") {
            throw GradleException("Unexpected static statx host output: ${fileOutput.trim()}")
        }
    }
}

// Static Linux positioned I/O example.
val linuxStaticPositionedIoExampleElf = layout.buildDirectory.file("examples/linux-static/positioned-io.elf")
val linuxStaticPositionedIoRoot = layout.buildDirectory.dir("tmp/linux-static-positioned-io-root")

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildLinuxStaticPositionedIoExample") {
    group = "verification"
    description = "Builds examples/linux-static/PositionedIo.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("PositionedIo.c", linuxStaticPositionedIoExampleElf)
}

tasks.register<JavaExec>("testLinuxStaticPositionedIoExample") {
    group = "verification"
    description = "Compiles a static musl positioned I/O example and verifies pread/pwrite behavior."

    dependsOn("classes", "buildLinuxStaticPositionedIoExample")
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

        val root = linuxStaticPositionedIoRoot.get().asFile
        delete(root)
        if (!root.mkdirs()) {
            throw GradleException("Failed to create example host root: $root")
        }

        setArgs(listOf("--host-root", root.absolutePath, linuxStaticPositionedIoExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (actualOutput != "positioned-io-ok\n") {
            throw GradleException("Unexpected static positioned I/O output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Static positioned I/O example wrote to stderr: $actualError")
        }

        val outputFile = linuxStaticPositionedIoRoot.get().file("positioned.txt").asFile
        val fileOutput = outputFile.readText(StandardCharsets.UTF_8)
        if (fileOutput != "0123AB6789") {
            throw GradleException("Unexpected static positioned I/O host output: ${fileOutput.trim()}")
        }
    }
}

// Static Linux event polling example.
val linuxStaticEventPollingExampleElf = layout.buildDirectory.file("examples/linux-static/event-polling.elf")

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildLinuxStaticEventPollingExample") {
    group = "verification"
    description = "Builds examples/linux-static/EventPolling.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("EventPolling.c", linuxStaticEventPollingExampleElf)
}

tasks.register<JavaExec>("testLinuxStaticEventPollingExample") {
    group = "verification"
    description = "Compiles a static musl eventfd/epoll example and verifies readiness polling."

    dependsOn("classes", "buildLinuxStaticEventPollingExample")
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
        setArgs(listOf(linuxStaticEventPollingExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (actualOutput != "event-polling-ok\n") {
            throw GradleException("Unexpected static event polling output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Static event polling example wrote to stderr: $actualError")
        }
    }
}

// Static Linux thread join example.
val linuxStaticThreadJoinExampleElf = layout.buildDirectory.file("examples/linux-static/thread-join.elf")

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildLinuxStaticThreadJoinExample") {
    group = "verification"
    description = "Builds examples/linux-static/ThreadJoin.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("ThreadJoin.c", linuxStaticThreadJoinExampleElf)
    additionalCompilerArguments.set(listOf("-O0", "-g0", "-no-pie", "-pthread"))
}

tasks.register<JavaExec>("testLinuxStaticThreadJoinExample") {
    group = "verification"
    description = "Compiles a static musl pthread example and verifies clone/futex-backed joins."

    dependsOn("classes", "buildLinuxStaticThreadJoinExample")
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
        setArgs(listOf(linuxStaticThreadJoinExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (actualOutput != "thread-join-ok\n") {
            throw GradleException("Unexpected static pthread output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Static pthread example wrote to stderr: $actualError")
        }
    }
}

// Static Linux runtime services example.
val linuxStaticRuntimeServicesExampleElf = layout.buildDirectory.file("examples/linux-static/runtime-services.elf")

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildLinuxStaticRuntimeServicesExample") {
    group = "verification"
    description = "Builds examples/linux-static/RuntimeServices.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("RuntimeServices.c", linuxStaticRuntimeServicesExampleElf)
}

tasks.register<JavaExec>("testLinuxStaticRuntimeServicesExample") {
    group = "verification"
    description = "Compiles a static musl runtime-services example and verifies memory, time, random, and resource syscalls."

    dependsOn("classes", "buildLinuxStaticRuntimeServicesExample")
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
        setArgs(listOf(linuxStaticRuntimeServicesExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (actualOutput != "runtime-services-ok\n") {
            throw GradleException("Unexpected static runtime services output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Static runtime services example wrote to stderr: $actualError")
        }
    }
}

// Static Linux process signals example.
val linuxStaticProcessSignalsExampleElf = layout.buildDirectory.file("examples/linux-static/process-signals.elf")

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildLinuxStaticProcessSignalsExample") {
    group = "verification"
    description = "Builds examples/linux-static/ProcessSignals.c as a static riscv64-linux-musl executable."

    configureLinuxStaticExample("ProcessSignals.c", linuxStaticProcessSignalsExampleElf)
}

tasks.register<JavaExec>("testLinuxStaticProcessSignalsExample") {
    group = "verification"
    description = "Compiles a static musl process and signal setup example and verifies process, signal, and prctl syscalls."

    dependsOn("classes", "buildLinuxStaticProcessSignalsExample")
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
        setArgs(listOf(linuxStaticProcessSignalsExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (actualOutput != "process-signals-ok\n") {
            throw GradleException("Unexpected static process signals output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("Static process signals example wrote to stderr: $actualError")
        }
    }
}

// Downloaded static Linux CoreMark example.
val coreMarkRevision = "1f483d5b8316753a742cbf5590caf5bd0a4e4777"
val coreMarkArchiveRoot = "coremark-$coreMarkRevision"
val coreMarkArchiveFile = layout.buildDirectory.file("downloads/coremark/coremark-$coreMarkRevision.zip")
val coreMarkSourceDirectory = layout.buildDirectory.dir("downloads/coremark/$coreMarkRevision")
val coreMarkExampleElf = layout.buildDirectory.file("examples/linux-static/coremark.elf")
val coreMarkSourcePaths = listOf(
    "core_list_join.c",
    "core_main.c",
    "core_matrix.c",
    "core_state.c",
    "core_util.c",
    "coremark.h",
    "posix/core_portme.c",
    "posix/core_portme.h",
    "posix/core_portme_posix_overrides.h"
)
val coreMarkSourceFiles = coreMarkSourcePaths.associateWith { sourcePath ->
    coreMarkSourceDirectory.map { directory -> directory.file(sourcePath) }
}

val downloadCoreMarkArchive by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    group = "build setup"
    description = "Downloads EEMBC CoreMark source archive revision $coreMarkRevision."

    src("https://github.com/eembc/coremark/archive/$coreMarkRevision.zip")
    dest(coreMarkArchiveFile.get().asFile)
    overwrite(false)
    tempAndMove(true)
    retries(3)
    connectTimeout(30_000)
    readTimeout(30_000)

    doFirst {
        val archive = coreMarkArchiveFile.get().asFile
        val parent = archive.parentFile
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw GradleException("Failed to create CoreMark archive directory: $parent")
        }
        if (archive.isFile && archive.length() == 0L) {
            delete(archive)
        }
    }
}

tasks.register("extractCoreMarkSources") {
    group = "build setup"
    description = "Extracts the selected EEMBC CoreMark sources used by the CoreMark example."

    dependsOn(downloadCoreMarkArchive)
    inputs.file(coreMarkArchiveFile)
    outputs.files(coreMarkSourceFiles.values)

    doLast {
        copy {
            from(zipTree(coreMarkArchiveFile.get().asFile)) {
                coreMarkSourcePaths.forEach { sourcePath ->
                    include("$coreMarkArchiveRoot/$sourcePath")
                }
                eachFile {
                    relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
                }
                includeEmptyDirs = false
            }
            into(coreMarkSourceDirectory)
        }
        val missingFiles = coreMarkSourceFiles.values
            .map { it.get().asFile }
            .filterNot { it.isFile }
        if (missingFiles.isNotEmpty()) {
            throw GradleException("CoreMark archive did not contain expected files: ${missingFiles.joinToString()}")
        }
    }
}

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildCoreMarkExample") {
    group = "verification"
    description = "Downloads EEMBC CoreMark and builds it as a static riscv64-linux-musl executable."

    dependsOn("extractCoreMarkSources")
    configureZigExecutable()
    sourceFiles.from(
        coreMarkSourceFiles.getValue("core_main.c"),
        coreMarkSourceFiles.getValue("core_list_join.c"),
        coreMarkSourceFiles.getValue("core_matrix.c"),
        coreMarkSourceFiles.getValue("core_state.c"),
        coreMarkSourceFiles.getValue("core_util.c"),
        coreMarkSourceFiles.getValue("posix/core_portme.c")
    )
    outputFile.set(coreMarkExampleElf)
    localCacheDirectory.set(layout.buildDirectory.dir("zig-local-cache"))
    globalCacheDirectory.set(layout.buildDirectory.dir("zig-global-cache"))
    additionalCompilerArguments.set(
        listOf(
            "-O2",
            "-g0",
            "-no-pie",
            "-DPERFORMANCE_RUN=1",
            "-DSEED_METHOD=SEED_VOLATILE",
            "-DITERATIONS=6000",
            "-DFLAGS_STR=\\\"-O2 -static -DPERFORMANCE_RUN=1 -DSEED_METHOD=SEED_VOLATILE -DITERATIONS=6000\\\"",
            "-I${coreMarkSourceDirectory.get().asFile.absolutePath}",
            "-I${coreMarkSourceDirectory.get().dir("posix").asFile.absolutePath}"
        )
    )
}

tasks.register<JavaExec>("runCoreMarkExample") {
    group = "verification"
    description = "Runs the downloaded static CoreMark example with the GraalRISCV CLI."

    dependsOn("classes", "buildCoreMarkExample")
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass = mainClassName
    jvmArgs(applicationDefaultJvmArgs)

    doFirst {
        setArgs(listOf(coreMarkExampleElf.get().asFile.absolutePath))
    }
}

tasks.register<JavaExec>("testCoreMarkExample") {
    group = "verification"
    description = "Runs the downloaded static CoreMark example and verifies CoreMark validation output."

    dependsOn("classes", "buildCoreMarkExample")
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
        setArgs(listOf(coreMarkExampleElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (!actualOutput.contains("Correct operation validated.")) {
            throw GradleException("CoreMark validation did not succeed: ${actualOutput.trim()}")
        }
        if (actualOutput.contains("Errors detected")) {
            throw GradleException("CoreMark reported errors: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("CoreMark example wrote to stderr: $actualError")
        }
    }
}

// Example aggregate checks.

tasks.register("checkHelloWorldExample") {
    group = "verification"
    description = "Runs every available Hello World example smoke check."

    dependsOn(
        "testHelloWorldExample",
        "testGoHelloWorldExample",
        "testLinuxStaticPrintfExample",
        "testLinuxStaticArgvExample",
        "testLinuxStaticFileIoExample",
        "testLinuxStaticDirectoryListExample",
        "testLinuxStaticFileMutationExample",
        "testLinuxStaticWorkingDirectoryExample",
        "testLinuxStaticFilesystemStatusExample",
        "testLinuxStaticStatxMetadataExample",
        "testLinuxStaticPositionedIoExample",
        "testLinuxStaticEventPollingExample",
        "testLinuxStaticThreadJoinExample",
        "testLinuxStaticRuntimeServicesExample",
        "testLinuxStaticProcessSignalsExample",
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
    dependsOn("testLinuxStaticFilesystemStatusExample")
    dependsOn("testLinuxStaticStatxMetadataExample")
    dependsOn("testLinuxStaticPositionedIoExample")
    dependsOn("testLinuxStaticEventPollingExample")
    dependsOn("testLinuxStaticThreadJoinExample")
    dependsOn("testLinuxStaticRuntimeServicesExample")
    dependsOn("testLinuxStaticProcessSignalsExample")
}
