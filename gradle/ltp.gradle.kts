import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Sync
import org.glavo.ltp.LtpSyscallCoverageTask

fun downloadFile(path: String) = providers.provider { layout.projectDirectory.file("downloads/$path") }
fun downloadDirectory(path: String) = providers.provider { layout.projectDirectory.dir("downloads/$path") }

val ltpRevision = "20250930"
val ltpArchiveRoot = "ltp-$ltpRevision"
val ltpArchiveFile = downloadFile("ltp/ltp-$ltpRevision.zip")
val ltpSourceDirectory = downloadDirectory("ltp/$ltpRevision")
val ltpRequiredPaths = listOf(
    "README.rst",
    "runtest/syscalls",
    "include/lapi/syscalls/arm64.in",
    "testcases/kernel/syscalls"
)

val downloadLtpArchive by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    group = "build setup"
    description = "Downloads LTP source archive release $ltpRevision."

    src("https://github.com/linux-test-project/ltp/archive/refs/tags/$ltpRevision.zip")
    dest(ltpArchiveFile.get().asFile)
    overwrite(false)
    tempAndMove(true)
    retries(3)
    connectTimeout(30_000)
    readTimeout(30_000)

    doFirst {
        val archive = ltpArchiveFile.get().asFile
        val parent = archive.parentFile
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw GradleException("Failed to create LTP archive directory: $parent")
        }
        if (archive.isFile && archive.length() == 0L) {
            delete(archive)
        }
    }
}

tasks.register<Sync>("extractLtpSources") {
    group = "build setup"
    description = "Extracts the pinned LTP source archive."

    dependsOn(downloadLtpArchive)
    from(zipTree(ltpArchiveFile.get().asFile)) {
        include("$ltpArchiveRoot/**")
        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
        includeEmptyDirs = false
    }
    into(ltpSourceDirectory)

    doLast {
        val sourceDirectory = ltpSourceDirectory.get().asFile
        val missingPaths = ltpRequiredPaths
            .map { sourceDirectory.resolve(it) }
            .filterNot { it.exists() }
        if (missingPaths.isNotEmpty()) {
            throw GradleException("LTP archive did not contain expected paths: ${missingPaths.joinToString()}")
        }
    }
}

tasks.register<LtpSyscallCoverageTask>("ltpSyscallCoverage") {
    group = "verification"
    description = "Generates an LTP syscall coverage report for LinuxGuestSyscalls."

    dependsOn("extractLtpSources")
    ltpSyscallsFile.set(ltpSourceDirectory.map { it.file("runtest/syscalls") })
    linuxGuestSyscallsFile.set(layout.projectDirectory.file("src/main/java/org/glavo/riscv/runtime/LinuxGuestSyscalls.java"))
    // LTP 20250930 does not ship riscv.in; arm64.in is the closest asm-generic 64-bit syscall filter.
    abiSyscallsFile.set(ltpSourceDirectory.map { it.file("include/lapi/syscalls/arm64.in") })
    outputFile.set(layout.buildDirectory.file("reports/ltp/syscall-coverage.md"))
}
