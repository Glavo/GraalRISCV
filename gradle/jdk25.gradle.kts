import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.tasks.SourceSetContainer

val jdk25ApplicationExtension = extensions.getByType<JavaApplication>()
val jdk25SourceSets = extensions.getByType<SourceSetContainer>()
val jdk25MainClassName = jdk25ApplicationExtension.mainClass.get()
val jdk25ApplicationDefaultJvmArgs = jdk25ApplicationExtension.applicationDefaultJvmArgs.toList()

fun downloadFile(path: String) = providers.provider { layout.projectDirectory.file("downloads/$path") }

val jdk25Version = providers.gradleProperty("jdk25Version").orElse("25.0.3+11").get()
val jdk25FeatureVersion = jdk25Version.substringBefore("+")
val jdk25Platform = providers.gradleProperty("jdk25Platform").orElse("linux-riscv64").get()
val jdk25ArchiveName = "bellsoft-jdk$jdk25Version-$jdk25Platform.tar.gz"
val jdk25TarName = jdk25ArchiveName.removeSuffix(".gz")
val jdk25ArchiveRoot = providers.gradleProperty("jdk25ArchiveRoot").orElse("jdk-$jdk25FeatureVersion").get()
val jdk25GuestMemorySize =
    providers.gradleProperty("jdk25GuestMemorySize").orElse("140737488355328").get()
val jdk25GuestJavaArgs = providers.gradleProperty("jdk25GuestJavaArgs")
    .map { value -> value.split(Regex("\\s+")).filter { it.isNotEmpty() } }
    .orElse(listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:-UseRVC", "-Xint"))
    .get()
val jdk25ArchiveUrl =
    providers.gradleProperty("jdk25ArchiveUrl")
        .orElse("https://download.bell-sw.com/java/$jdk25Version/$jdk25ArchiveName")
        .get()
val jdk25ArchiveFile = downloadFile("jdk25/$jdk25Version/$jdk25ArchiveName")
val jdk25TarFile = downloadFile("jdk25/$jdk25Version/$jdk25TarName")
val jdk25MountPath = "/opt/jdk25"
val jdk25GuestJavaPath = "$jdk25MountPath/$jdk25ArchiveRoot/bin/java"
val jdk25GuestJshellPath = "$jdk25MountPath/$jdk25ArchiveRoot/bin/jshell"
val jdk25GuestTempDirectory = layout.buildDirectory.dir("tmp/jdk25-guest-tmp")

val jdk25UbuntuBaseVersion = "26.04"
val jdk25UbuntuBaseArchitecture = "riscv64"
val jdk25UbuntuBaseTarName =
    "ubuntu-base-$jdk25UbuntuBaseVersion-base-$jdk25UbuntuBaseArchitecture.tar"
val jdk25UbuntuBaseTarFile =
    downloadFile("ubuntu-base/$jdk25UbuntuBaseVersion/$jdk25UbuntuBaseTarName")

val downloadJdk25Archive by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    group = "build setup"
    description = "Downloads BellSoft JDK $jdk25Version for $jdk25Platform."

    src(jdk25ArchiveUrl)
    dest(jdk25ArchiveFile.get().asFile)
    overwrite(false)
    tempAndMove(true)
    retries(3)
    connectTimeout(30_000)
    readTimeout(30_000)

    doFirst {
        val archive = jdk25ArchiveFile.get().asFile
        val parent = archive.parentFile
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw GradleException("Failed to create JDK 25 archive directory: $parent")
        }
        if (archive.isFile && archive.length() == 0L) {
            delete(archive)
        }
    }
}

tasks.register("decompressJdk25Archive") {
    group = "build setup"
    description = "Decompresses the downloaded BellSoft JDK 25 gzip stream to a tar file without unpacking the tar."

    dependsOn(downloadJdk25Archive)
    inputs.file(jdk25ArchiveFile)
    outputs.file(jdk25TarFile)

    doLast {
        val archive = jdk25ArchiveFile.get().asFile
        val tar = jdk25TarFile.get().asFile
        val parent = tar.parentFile
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw GradleException("Failed to create JDK 25 tar directory: $parent")
        }

        val temporaryTar = tar.resolveSibling("${tar.name}.tmp")
        try {
            GZIPInputStream(archive.inputStream().buffered()).use { input ->
                temporaryTar.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
            if (!temporaryTar.isFile || temporaryTar.length() == 0L) {
                throw GradleException("Decompressed JDK 25 tar is empty: $tar")
            }
            Files.move(temporaryTar.toPath(), tar.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (exception: java.io.IOException) {
            throw GradleException("Failed to decompress JDK 25 gzip archive: $archive", exception)
        } finally {
            delete(temporaryTar)
        }
    }
}

tasks.register<JavaExec>("testJdk25JshellVersion") {
    group = "verification"
    description = "Runs jshell --version from the downloaded BellSoft JDK 25 RISC-V archive."

    dependsOn("classes", "decompressUbuntuBaseImage", "decompressJdk25Archive")
    classpath = jdk25SourceSets.named("main").get().runtimeClasspath
    mainClass = jdk25MainClassName
    jvmArgs(jdk25ApplicationDefaultJvmArgs)
    isIgnoreExitValue = true

    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    standardOutput = stdout
    errorOutput = stderr

    doFirst {
        stdout.reset()
        stderr.reset()
        setArgs(listOf(
            "--memory-size", jdk25GuestMemorySize,
            "--mount", "type=tar,src=${jdk25UbuntuBaseTarFile.get().asFile.absolutePath},dst=/",
            "--mount", "type=tar,src=${jdk25TarFile.get().asFile.absolutePath},dst=$jdk25MountPath",
            "--mount", "type=tmpfs,dst=/tmp",
            "--home", "/tmp",
            "--guest-program", jdk25GuestJshellPath,
        ) + jdk25GuestJavaArgs.map { "-J$it" } + listOf(
            "--version"
        ))
    }

    doLast {
        val exitCode = executionResult.get().exitValue
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        val actualError = stderr.toString(StandardCharsets.UTF_8)
        val combinedOutput = actualOutput + actualError
        if (exitCode != 0) {
            throw GradleException("$name exited with $exitCode. stderr: $actualError")
        }
        if (!combinedOutput.contains("jshell $jdk25FeatureVersion")) {
            throw GradleException("$name wrote unexpected output. stdout: $actualOutput stderr: $actualError")
        }
    }
}

tasks.register<JavaExec>("testJdk25JavaVersion") {
    group = "verification"
    description = "Runs java -version from the downloaded BellSoft JDK 25 RISC-V archive."

    dependsOn("classes", "decompressUbuntuBaseImage", "decompressJdk25Archive")
    classpath = jdk25SourceSets.named("main").get().runtimeClasspath
    mainClass = jdk25MainClassName
    jvmArgs(jdk25ApplicationDefaultJvmArgs)
    isIgnoreExitValue = true

    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    standardOutput = stdout
    errorOutput = stderr

    doFirst {
        stdout.reset()
        stderr.reset()
        val guestTemp = jdk25GuestTempDirectory.get().asFile
        if (!guestTemp.isDirectory && !guestTemp.mkdirs()) {
            throw GradleException("Failed to create JDK 25 guest temp directory: $guestTemp")
        }
        setArgs(listOf(
            "--memory-size", jdk25GuestMemorySize,
            "--mount", "type=tar,src=${jdk25UbuntuBaseTarFile.get().asFile.absolutePath},dst=/",
            "--mount", "type=tar,src=${jdk25TarFile.get().asFile.absolutePath},dst=$jdk25MountPath",
            "--mount", "type=bind,src=${guestTemp.absolutePath},dst=/tmp,rw",
            "--home", "/tmp",
            "--guest-program", jdk25GuestJavaPath,
        ) + jdk25GuestJavaArgs + listOf(
            "-version"
        ))
    }

    doLast {
        val exitCode = executionResult.get().exitValue
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        val actualError = stderr.toString(StandardCharsets.UTF_8)
        val combinedOutput = actualOutput + actualError
        if (exitCode != 0) {
            throw GradleException("$name exited with $exitCode. stderr: $actualError")
        }
        if (!combinedOutput.contains("openjdk version \"$jdk25FeatureVersion\"")) {
            throw GradleException("$name wrote unexpected output. stdout: $actualOutput stderr: $actualError")
        }
    }
}

tasks.register<JavaExec>("runJdk25JavaVersion") {
    group = "verification"
    description = "Runs java -version from the downloaded BellSoft JDK 25 RISC-V archive."

    dependsOn("classes", "decompressUbuntuBaseImage", "decompressJdk25Archive")
    classpath = jdk25SourceSets.named("main").get().runtimeClasspath
    mainClass = jdk25MainClassName
    jvmArgs(jdk25ApplicationDefaultJvmArgs)

    doFirst {
        val guestTemp = jdk25GuestTempDirectory.get().asFile
        if (!guestTemp.isDirectory && !guestTemp.mkdirs()) {
            throw GradleException("Failed to create JDK 25 guest temp directory: $guestTemp")
        }
        setArgs(listOf(
            "--memory-size", jdk25GuestMemorySize,
            "--mount", "type=tar,src=${jdk25UbuntuBaseTarFile.get().asFile.absolutePath},dst=/",
            "--mount", "type=tar,src=${jdk25TarFile.get().asFile.absolutePath},dst=$jdk25MountPath",
            "--mount", "type=bind,src=${guestTemp.absolutePath},dst=/tmp,rw",
            "--home", "/tmp",
            "--guest-program", jdk25GuestJavaPath,
        ) + jdk25GuestJavaArgs + listOf(
            "-version"
        ))
    }
}
