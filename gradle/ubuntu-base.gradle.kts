import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.charset.StandardCharsets
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.tasks.SourceSetContainer

val applicationExtension = extensions.getByType<JavaApplication>()
val sourceSets = extensions.getByType<SourceSetContainer>()
val ubuntuBaseMainClassName = applicationExtension.mainClass.get()
val ubuntuBaseApplicationDefaultJvmArgs = applicationExtension.applicationDefaultJvmArgs.toList()

val ubuntuBaseVersion = "26.04"
val ubuntuBaseArchitecture = "riscv64"
val ubuntuBaseArchiveName = "ubuntu-base-$ubuntuBaseVersion-base-$ubuntuBaseArchitecture.tar.gz"
val ubuntuBaseTarName = ubuntuBaseArchiveName.removeSuffix(".gz")
val ubuntuBaseArchiveFile = layout.buildDirectory.file("downloads/ubuntu-base/$ubuntuBaseVersion/$ubuntuBaseArchiveName")
val ubuntuBaseTarFile = layout.buildDirectory.file("downloads/ubuntu-base/$ubuntuBaseVersion/$ubuntuBaseTarName")

fun registerUbuntuBaseSmokeTest(
    taskName: String,
    descriptionText: String,
    guestArguments: List<String>,
    expectedStdout: String
) {
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        description = descriptionText

        dependsOn("classes", "decompressUbuntuBaseImage")
        classpath = sourceSets.named("main").get().runtimeClasspath
        mainClass = ubuntuBaseMainClassName
        jvmArgs(ubuntuBaseApplicationDefaultJvmArgs)
        isIgnoreExitValue = true

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        standardOutput = stdout
        errorOutput = stderr

        doFirst {
            stdout.reset()
            stderr.reset()
            setArgs(listOf(
                "--mount", "/=${ubuntuBaseTarFile.get().asFile.absolutePath}"
            ) + guestArguments)
        }

        doLast {
            val exitCode = executionResult.get().exitValue
            val actualOutput = stdout.toString(StandardCharsets.UTF_8)
            val actualError = stderr.toString(StandardCharsets.UTF_8)
            if (exitCode != 0) {
                throw GradleException("$name exited with $exitCode. stderr: $actualError")
            }
            if (actualOutput != expectedStdout) {
                throw GradleException("$name wrote unexpected stdout: $actualOutput")
            }
            if (actualError.isNotEmpty()) {
                throw GradleException("$name wrote to stderr: $actualError")
            }
        }
    }
}

val downloadUbuntuBaseImage by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    group = "build setup"
    description = "Downloads Ubuntu Base $ubuntuBaseVersion for $ubuntuBaseArchitecture."

    src("https://cdimage.ubuntu.com/ubuntu-base/releases/$ubuntuBaseVersion/release/$ubuntuBaseArchiveName")
    dest(ubuntuBaseArchiveFile.get().asFile)
    overwrite(false)
    tempAndMove(true)
    retries(3)
    connectTimeout(30_000)
    readTimeout(30_000)

    doFirst {
        val archive = ubuntuBaseArchiveFile.get().asFile
        val parent = archive.parentFile
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw GradleException("Failed to create Ubuntu Base archive directory: $parent")
        }
        if (archive.isFile && archive.length() == 0L) {
            delete(archive)
        }
    }
}

tasks.register("decompressUbuntuBaseImage") {
    group = "build setup"
    description = "Decompresses the downloaded Ubuntu Base gzip stream to a tar file without unpacking the tar."

    dependsOn(downloadUbuntuBaseImage)
    inputs.file(ubuntuBaseArchiveFile)
    outputs.file(ubuntuBaseTarFile)

    doLast {
        val archive = ubuntuBaseArchiveFile.get().asFile
        val tar = ubuntuBaseTarFile.get().asFile
        val parent = tar.parentFile
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw GradleException("Failed to create Ubuntu Base tar directory: $parent")
        }

        val temporaryTar = tar.resolveSibling("${tar.name}.tmp")
        try {
            GZIPInputStream(archive.inputStream().buffered()).use { input ->
                temporaryTar.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
            if (!temporaryTar.isFile || temporaryTar.length() == 0L) {
                throw GradleException("Decompressed Ubuntu Base tar is empty: $tar")
            }
            Files.move(temporaryTar.toPath(), tar.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (exception: java.io.IOException) {
            throw GradleException("Failed to decompress Ubuntu Base gzip archive: $archive", exception)
        } finally {
            delete(temporaryTar)
        }
    }
}

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseTrue",
    "Runs /usr/bin/true from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/true"),
    ""
)

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseBash",
    "Runs /usr/bin/bash from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/bash", "-c", "echo bash-ok"),
    "bash-ok\n"
)
