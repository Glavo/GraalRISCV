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

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseLs",
    "Runs /usr/bin/ls from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/ls", "-1", "/usr/bin/bash"),
    "/usr/bin/bash\n"
)

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseCat",
    "Runs /usr/bin/cat from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/cat", "/etc/issue"),
    "Ubuntu 26.04 LTS \\n \\l\n\n"
)

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseHead",
    "Runs /usr/bin/head from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/head", "-n", "1", "/etc/issue"),
    "Ubuntu 26.04 LTS \\n \\l\n"
)

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseWc",
    "Runs /usr/bin/wc from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/wc", "-c", "/etc/issue"),
    "24 /etc/issue\n"
)

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseSha256sum",
    "Runs /usr/bin/sha256sum from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/sha256sum", "/etc/issue"),
    "547700963039ce6e3779f128ec369b54f8559db50e9d20fa39459f5b5b7434de  /etc/issue\n"
)

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseGrep",
    "Runs /usr/bin/grep from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/grep", "Ubuntu", "/etc/issue"),
    "Ubuntu 26.04 LTS \\n \\l\n"
)

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseReadlink",
    "Runs /usr/bin/readlink from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/readlink", "-f", "/usr/bin/bash"),
    "/usr/bin/bash\n"
)

registerUbuntuBaseSmokeTest(
    "testUbuntuBasePwd",
    "Runs /usr/bin/pwd from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/pwd"),
    "/\n"
)

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseId",
    "Runs /usr/bin/id from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/id", "-u"),
    "1000\n"
)

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseUname",
    "Runs /usr/bin/uname from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/uname", "-m"),
    "riscv64\n"
)

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseDu",
    "Runs /usr/bin/du from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/du", "-s", "/usr/bin/bash"),
    "1288\t/usr/bin/bash\n"
)

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseStat",
    "Runs /usr/bin/stat from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/stat", "-c", "%s", "/usr/bin/bash"),
    "1318632\n"
)

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseDf",
    "Runs /usr/bin/df from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/df", "-P", "-k", "/"),
    "Filesystem     1024-blocks  Used Available Capacity Mounted on\n" +
            "graalriscv         4194304     0   4194304       0% /\n"
)

registerUbuntuBaseSmokeTest(
    "testUbuntuBaseFind",
    "Runs /usr/bin/find from the downloaded Ubuntu Base RISC-V root tar.",
    listOf("--guest-program", "/usr/bin/find", "/usr/bin", "-maxdepth", "1", "-name", "bash", "-print"),
    "/usr/bin/bash\n"
)
