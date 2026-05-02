import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.tasks.SourceSetContainer

val fastfetchApplicationExtension = extensions.getByType<JavaApplication>()
val fastfetchSourceSets = extensions.getByType<SourceSetContainer>()
val fastfetchMainClassName = fastfetchApplicationExtension.mainClass.get()
val fastfetchApplicationDefaultJvmArgs = fastfetchApplicationExtension.applicationDefaultJvmArgs.toList()

fun downloadFile(path: String) = providers.provider { layout.projectDirectory.file("downloads/$path") }

val fastfetchVersion = providers.gradleProperty("fastfetchVersion").orElse("2.62.1").get()
val fastfetchPlatform = providers.gradleProperty("fastfetchPlatform").orElse("linux-riscv64").get()
val fastfetchArchiveBaseName = "fastfetch-$fastfetchPlatform"
val fastfetchArchiveName = "$fastfetchArchiveBaseName.tar.gz"
val fastfetchTarName = fastfetchArchiveName.removeSuffix(".gz")
val fastfetchArchiveFile = downloadFile("fastfetch/$fastfetchVersion/$fastfetchArchiveName")
val fastfetchTarFile = downloadFile("fastfetch/$fastfetchVersion/$fastfetchTarName")
val fastfetchMountPath = "/opt/fastfetch"
val fastfetchGuestProgramPath = "$fastfetchMountPath/$fastfetchArchiveBaseName/usr/bin/fastfetch"
val fastfetchUbuntuBaseVersion = "26.04"
val fastfetchUbuntuBaseArchitecture = "riscv64"
val fastfetchUbuntuBaseTarName =
    "ubuntu-base-$fastfetchUbuntuBaseVersion-base-$fastfetchUbuntuBaseArchitecture.tar"
val fastfetchUbuntuBaseTarFile =
    downloadFile("ubuntu-base/$fastfetchUbuntuBaseVersion/$fastfetchUbuntuBaseTarName")

val downloadFastfetchArchive by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    group = "build setup"
    description = "Downloads fastfetch $fastfetchVersion for $fastfetchPlatform from GitHub releases."

    src("https://github.com/fastfetch-cli/fastfetch/releases/download/$fastfetchVersion/$fastfetchArchiveName")
    dest(fastfetchArchiveFile.get().asFile)
    overwrite(false)
    tempAndMove(true)
    retries(3)
    connectTimeout(30_000)
    readTimeout(30_000)

    doFirst {
        val archive = fastfetchArchiveFile.get().asFile
        val parent = archive.parentFile
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw GradleException("Failed to create fastfetch archive directory: $parent")
        }
        if (archive.isFile && archive.length() == 0L) {
            delete(archive)
        }
    }
}

tasks.register("decompressFastfetchArchive") {
    group = "build setup"
    description = "Decompresses the downloaded fastfetch gzip stream to a tar file without unpacking the tar."

    dependsOn(downloadFastfetchArchive)
    inputs.file(fastfetchArchiveFile)
    outputs.file(fastfetchTarFile)

    doLast {
        val archive = fastfetchArchiveFile.get().asFile
        val tar = fastfetchTarFile.get().asFile
        val parent = tar.parentFile
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw GradleException("Failed to create fastfetch tar directory: $parent")
        }

        val temporaryTar = tar.resolveSibling("${tar.name}.tmp")
        try {
            GZIPInputStream(archive.inputStream().buffered()).use { input ->
                temporaryTar.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
            if (!temporaryTar.isFile || temporaryTar.length() == 0L) {
                throw GradleException("Decompressed fastfetch tar is empty: $tar")
            }
            Files.move(temporaryTar.toPath(), tar.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (exception: java.io.IOException) {
            throw GradleException("Failed to decompress fastfetch gzip archive: $archive", exception)
        } finally {
            delete(temporaryTar)
        }
    }
}

tasks.register<JavaExec>("testFastfetchVersion") {
    group = "verification"
    description = "Runs fastfetch --version from the downloaded RISC-V Linux release tar."

    dependsOn("classes", "decompressUbuntuBaseImage", "decompressFastfetchArchive")
    classpath = fastfetchSourceSets.named("main").get().runtimeClasspath
    mainClass = fastfetchMainClassName
    jvmArgs(fastfetchApplicationDefaultJvmArgs)
    isIgnoreExitValue = true

    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    standardOutput = stdout
    errorOutput = stderr

    doFirst {
        stdout.reset()
        stderr.reset()
        setArgs(listOf(
            "--mount", "type=tar,src=${fastfetchUbuntuBaseTarFile.get().asFile.absolutePath},dst=/",
            "--mount", "type=tar,src=${fastfetchTarFile.get().asFile.absolutePath},dst=$fastfetchMountPath",
            "--guest-program", fastfetchGuestProgramPath,
            "--version"
        ))
    }

    doLast {
        val exitCode = executionResult.get().exitValue
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (exitCode != 0) {
            throw GradleException("$name exited with $exitCode. stderr: $actualError")
        }
        if (!actualOutput.startsWith("fastfetch $fastfetchVersion")) {
            throw GradleException("$name wrote unexpected stdout: $actualOutput")
        }
        if (actualError.isNotEmpty()) {
            throw GradleException("$name wrote to stderr: $actualError")
        }
    }
}

tasks.register<JavaExec>("testFastfetch") {
    group = "verification"
    description = "Runs fastfetch from the downloaded RISC-V Linux release tar and verifies key output fields."

    dependsOn("classes", "decompressUbuntuBaseImage", "decompressFastfetchArchive")
    classpath = fastfetchSourceSets.named("main").get().runtimeClasspath
    mainClass = fastfetchMainClassName
    jvmArgs(fastfetchApplicationDefaultJvmArgs)
    isIgnoreExitValue = true

    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    standardOutput = stdout
    errorOutput = stderr

    doFirst {
        stdout.reset()
        stderr.reset()
        setArgs(listOf(
            "--mount", "type=tar,src=${fastfetchUbuntuBaseTarFile.get().asFile.absolutePath},dst=/",
            "--mount", "type=tar,src=${fastfetchTarFile.get().asFile.absolutePath},dst=$fastfetchMountPath",
            "--guest-program", fastfetchGuestProgramPath,
            "--",
            "--logo", "none",
            "--pipe", "true",
            "--show-errors", "true",
            "--structure",
            "Title:Separator:OS:Host:Kernel:Uptime:Packages:Shell:Terminal:CPU:Memory:Swap:Disk:LocalIp:Battery:PowerAdapter:Locale"
        ))
    }

    doLast {
        val exitCode = executionResult.get().exitValue
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (exitCode != 0) {
            throw GradleException("$name exited with $exitCode. stderr: $actualError")
        }

        val requiredOutputFragments = listOf(
            "user@localhost",
            "OS: Ubuntu 26.04",
            "Host: GraalRISCV",
            "Kernel: Linux",
            "Packages: 86 (dpkg)",
            "Shell: sh",
            "Terminal: xterm-256color",
            "CPU: glavo,graalriscv rv64gc",
            "Memory:",
            "/ 4.00 GiB",
            "Swap: Unused",
            "Disk (/):",
            "graalriscv",
            "Local IP (eth0): 10.0.2.15/24",
            "Battery: No batteries found",
            "Power Adapter: No power adapters found",
            "Locale: C"
        )
        val missingFragments = requiredOutputFragments.filterNot(actualOutput::contains)
        if (missingFragments.isNotEmpty()) {
            throw GradleException("$name output missed expected fragments $missingFragments. stdout: $actualOutput")
        }
        if (actualError.isNotEmpty()) {
            throw GradleException("$name wrote to stderr: $actualError")
        }
    }
}

tasks.register<JavaExec>("runFastfetch") {
    group = "verification"
    description = "Runs fastfetch from the downloaded RISC-V Linux release tar."

    dependsOn("classes", "decompressUbuntuBaseImage", "decompressFastfetchArchive")
    classpath = fastfetchSourceSets.named("main").get().runtimeClasspath
    mainClass = fastfetchMainClassName
    jvmArgs(fastfetchApplicationDefaultJvmArgs)

    doFirst {
        setArgs(listOf(
            "--mount", "type=tar,src=${fastfetchUbuntuBaseTarFile.get().asFile.absolutePath},dst=/",
            "--mount", "type=tar,src=${fastfetchTarFile.get().asFile.absolutePath},dst=$fastfetchMountPath",
            "--guest-program", fastfetchGuestProgramPath
        ))
    }
}

tasks.named("checkShowcaseExamples") {
    dependsOn("testFastfetch")
}
