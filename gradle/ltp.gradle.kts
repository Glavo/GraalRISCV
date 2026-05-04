import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.gradle.api.file.RelativePath
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.glavo.zig.RiscVLinuxMuslStaticZigCCTask
import org.glavo.zig.ZigToolchainSupport
import org.glavo.ltp.LtpSyscallCoverageTask

fun downloadFile(path: String) = providers.provider { layout.projectDirectory.file("downloads/$path") }
fun downloadDirectory(path: String) = providers.provider { layout.projectDirectory.dir("downloads/$path") }

val applicationExtension = extensions.getByType<JavaApplication>()
val sourceSets = extensions.getByType<SourceSetContainer>()
val zigToolchain = ZigToolchainSupport.getOrCreate(project)
val ltpApplicationMainClassName = applicationExtension.mainClass.get()
val applicationDefaultJvmArgs = applicationExtension.applicationDefaultJvmArgs.toList()
val ltpJavaExecJvmArgs = if ("--enable-native-access=ALL-UNNAMED" in applicationDefaultJvmArgs) {
    applicationDefaultJvmArgs
} else {
    applicationDefaultJvmArgs + "--enable-native-access=ALL-UNNAMED"
}
val ltpRevision = "20250930"
val ltpArchiveRoot = "ltp-$ltpRevision"
val ltpArchiveFile = downloadFile("ltp/ltp-$ltpRevision.zip")
val ltpSourceDirectory = downloadDirectory("ltp/$ltpRevision")
val ltpAbiSyscallsFile = ltpSourceDirectory.map { it.file("include/lapi/syscalls/arm64.in") }
val ltpSyscallSmokeSource = layout.buildDirectory.file("generated/ltp/syscall-smoke/LtpSyscallSmoke.c")
val ltpSyscallSmokeElf = layout.buildDirectory.file("ltp/syscall-smoke.elf")
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

fun RiscVLinuxMuslStaticZigCCTask.configureZigExecutable() {
    zigToolchain.configureExecutable(this, zigExecutable)
}

fun parseLtpAbiSyscalls(file: File): Map<String, Int> {
    return file.readLines()
        .map { line -> line.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .associate { line ->
            val fields = line.split(Regex("\\s+"))
            if (fields.size < 2) {
                throw GradleException("Malformed LTP syscall ABI line: $line")
            }
            fields[0] to fields[1].toInt()
        }
}

fun generateLtpSyscallSmokeSource(abiFile: File, outputFile: File) {
    val requiredSyscalls = listOf(
        "getcwd",
        "write",
        "getpid",
        "getppid",
        "getuid",
        "geteuid",
        "getgid",
        "getegid",
        "gettid",
        "uname",
        "brk",
        "clock_gettime",
        "clock_getres",
        "getrandom"
    )
    val syscallNumbers = parseLtpAbiSyscalls(abiFile)
    val missingSyscalls = requiredSyscalls.filterNot(syscallNumbers::containsKey)
    if (missingSyscalls.isNotEmpty()) {
        throw GradleException("LTP ABI syscall table is missing entries: ${missingSyscalls.joinToString()}")
    }

    val macros = requiredSyscalls.joinToString(separator = "\n") { syscall ->
        "#define LTP_NR_${syscall.uppercase()} ${syscallNumbers.getValue(syscall)}"
    }

    outputFile.parentFile.mkdirs()
    outputFile.writeText(
        """
        // Copyright (c) 2026 Glavo
        // SPDX-License-Identifier: MPL-2.0
        //
        // Generated from the LTP $ltpRevision asm-generic 64-bit syscall table.

        #include <errno.h>
        #include <stdint.h>
        #include <string.h>
        #include <sys/syscall.h>
        #include <sys/utsname.h>
        #include <time.h>
        #include <unistd.h>

        $macros

        static int failures;

        static void write_text(int fd, const char *text) {
            size_t length = strlen(text);
            while (length > 0) {
                long written = syscall(LTP_NR_WRITE, fd, text, length);
                if (written <= 0) {
                    return;
                }
                text += written;
                length -= (size_t) written;
            }
        }

        static void fail(const char *syscall_name, const char *reason) {
            write_text(2, syscall_name);
            write_text(2, ": ");
            write_text(2, reason);
            write_text(2, "\n");
            failures++;
        }

        static void require_nonnegative(const char *syscall_name, long value) {
            if (value < 0) {
                fail(syscall_name, "returned a negative value");
            }
        }

        static void require_positive(const char *syscall_name, long value) {
            if (value <= 0) {
                fail(syscall_name, "returned a non-positive value");
            }
        }

        static void test_identity_syscalls(void) {
            require_positive("getpid", syscall(LTP_NR_GETPID));
            require_nonnegative("getppid", syscall(LTP_NR_GETPPID));
            require_nonnegative("getuid", syscall(LTP_NR_GETUID));
            require_nonnegative("geteuid", syscall(LTP_NR_GETEUID));
            require_nonnegative("getgid", syscall(LTP_NR_GETGID));
            require_nonnegative("getegid", syscall(LTP_NR_GETEGID));
            require_positive("gettid", syscall(LTP_NR_GETTID));
        }

        static void test_getcwd(void) {
            char buffer[256];
            memset(buffer, 0, sizeof(buffer));
            long result = syscall(LTP_NR_GETCWD, buffer, sizeof(buffer));
            if (result <= 1) {
                fail("getcwd", "did not return a usable path length");
                return;
            }
            if (buffer[0] != '/') {
                fail("getcwd", "did not return an absolute path");
            }
        }

        static void test_uname(void) {
            struct utsname name;
            memset(&name, 0, sizeof(name));
            long result = syscall(LTP_NR_UNAME, &name);
            if (result != 0) {
                fail("uname", "did not return zero");
                return;
            }
            if (strcmp(name.machine, "riscv64") != 0) {
                fail("uname", "did not report riscv64 machine");
            }
            if (name.sysname[0] == '\0') {
                fail("uname", "did not report a sysname");
            }
        }

        static void test_clock_syscalls(void) {
            struct timespec time_value;
            struct timespec resolution;
            long result = syscall(LTP_NR_CLOCK_GETTIME, CLOCK_MONOTONIC, &time_value);
            if (result != 0) {
                fail("clock_gettime", "did not return zero");
            } else if (time_value.tv_nsec < 0 || time_value.tv_nsec >= 1000000000L) {
                fail("clock_gettime", "returned an invalid nanosecond field");
            }

            result = syscall(LTP_NR_CLOCK_GETRES, CLOCK_MONOTONIC, &resolution);
            if (result != 0) {
                fail("clock_getres", "did not return zero");
            } else if (resolution.tv_nsec < 0 || resolution.tv_nsec >= 1000000000L) {
                fail("clock_getres", "returned an invalid nanosecond field");
            }
        }

        static void test_memory_and_random_syscalls(void) {
            long current_break = syscall(LTP_NR_BRK, 0);
            require_positive("brk", current_break);

            unsigned char bytes[16];
            memset(bytes, 0, sizeof(bytes));
            long result = syscall(LTP_NR_GETRANDOM, bytes, sizeof(bytes), 0);
            if (result != (long) sizeof(bytes)) {
                fail("getrandom", "did not fill the requested byte count");
            }
        }

        int main(void) {
            test_identity_syscalls();
            test_getcwd();
            test_uname();
            test_clock_syscalls();
            test_memory_and_random_syscalls();

            if (failures != 0) {
                write_text(1, "ltp-syscall-smoke-failed\n");
                return 1;
            }

            write_text(1, "ltp-syscall-smoke-ok\n");
            return 0;
        }
        """.trimIndent(),
        Charsets.US_ASCII
    )
}

tasks.register("generateLtpSyscallSmokeSource") {
    group = "verification"
    description = "Generates a small LTP ABI-table driven syscall smoke test source."

    dependsOn("extractLtpSources")
    inputs.file(ltpAbiSyscallsFile)
    outputs.file(ltpSyscallSmokeSource)

    doLast {
        generateLtpSyscallSmokeSource(
            ltpAbiSyscallsFile.get().asFile,
            ltpSyscallSmokeSource.get().asFile
        )
    }
}

tasks.register<RiscVLinuxMuslStaticZigCCTask>("buildLtpSyscallSmoke") {
    group = "verification"
    description = "Builds the LTP ABI-table driven syscall smoke test as a static riscv64-linux-musl executable."

    dependsOn("generateLtpSyscallSmokeSource")
    configureZigExecutable()
    sourceFiles.from(ltpSyscallSmokeSource)
    outputFile.set(ltpSyscallSmokeElf)
    localCacheDirectory.set(layout.buildDirectory.dir("zig-local-cache"))
    globalCacheDirectory.set(layout.buildDirectory.dir("zig-global-cache"))
}

tasks.register<JavaExec>("testLtpSyscallSmoke") {
    group = "verification"
    description = "Runs the LTP ABI-table driven syscall smoke test through the JRISC-V CLI."

    dependsOn("classes", "buildLtpSyscallSmoke")
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass = ltpApplicationMainClassName
    jvmArgs(ltpJavaExecJvmArgs)

    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    standardOutput = stdout
    errorOutput = stderr

    doFirst {
        stdout.reset()
        stderr.reset()
        setArgs(listOf(ltpSyscallSmokeElf.get().asFile.absolutePath))
    }

    doLast {
        val actualOutput = stdout.toString(StandardCharsets.UTF_8)
        if (actualOutput != "ltp-syscall-smoke-ok\n") {
            throw GradleException("Unexpected LTP syscall smoke output: ${actualOutput.trim()}")
        }

        val actualError = stderr.toString(StandardCharsets.UTF_8)
        if (actualError.isNotEmpty()) {
            throw GradleException("LTP syscall smoke wrote to stderr: $actualError")
        }
    }
}

tasks.register("ltpSyscallCheck") {
    group = "verification"
    description = "Generates LTP syscall coverage and runs the LTP ABI-table driven syscall smoke test."

    dependsOn("ltpSyscallCoverage", "testLtpSyscallSmoke")
}
