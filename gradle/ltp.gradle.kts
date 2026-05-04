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
val ltpSyscallSmokeRoot = layout.buildDirectory.dir("tmp/ltp-syscall-smoke-root")
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
        "dup",
        "dup3",
        "fcntl",
        "mkdirat",
        "unlinkat",
        "renameat",
        "faccessat",
        "faccessat2",
        "openat",
        "close",
        "lseek",
        "read",
        "write",
        "readv",
        "writev",
        "pread64",
        "pwrite64",
        "newfstatat",
        "fstat",
        "ftruncate",
        "nanosleep",
        "getpid",
        "getppid",
        "getuid",
        "geteuid",
        "getgid",
        "getegid",
        "gettid",
        "times",
        "getgroups",
        "uname",
        "getrusage",
        "gettimeofday",
        "sysinfo",
        "brk",
        "clock_gettime",
        "clock_getres",
        "sched_getaffinity",
        "prlimit64",
        "renameat2",
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
        #include <fcntl.h>
        #include <stdint.h>
        #include <string.h>
        #include <sys/resource.h>
        #include <sys/stat.h>
        #include <sys/sysinfo.h>
        #include <sys/time.h>
        #include <sys/times.h>
        #include <sys/uio.h>
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

        static void test_process_resource_syscalls(void) {
            long group_count = syscall(LTP_NR_GETGROUPS, 0, 0);
            require_nonnegative("getgroups", group_count);
            if (group_count >= 0) {
                gid_t groups[16];
                long copied_groups = syscall(LTP_NR_GETGROUPS, 16, groups);
                if (copied_groups < group_count) {
                    fail("getgroups", "did not copy the reported group count");
                }
            }

            struct tms process_times;
            require_nonnegative("times", syscall(LTP_NR_TIMES, &process_times));

            struct rusage usage;
            if (syscall(LTP_NR_GETRUSAGE, RUSAGE_SELF, &usage) != 0) {
                fail("getrusage", "did not return zero");
            }

            struct timeval current_time;
            if (syscall(LTP_NR_GETTIMEOFDAY, &current_time, 0) != 0) {
                fail("gettimeofday", "did not return zero");
            } else if (current_time.tv_usec < 0 || current_time.tv_usec >= 1000000L) {
                fail("gettimeofday", "returned an invalid microsecond field");
            }

            struct sysinfo info;
            if (syscall(LTP_NR_SYSINFO, &info) != 0) {
                fail("sysinfo", "did not return zero");
            } else if (info.mem_unit == 0 || info.totalram == 0) {
                fail("sysinfo", "did not report usable memory totals");
            }

            struct rlimit limit;
            if (syscall(LTP_NR_PRLIMIT64, 0, RLIMIT_NOFILE, 0, &limit) != 0) {
                fail("prlimit64", "did not return zero for RLIMIT_NOFILE query");
            } else if (limit.rlim_cur > limit.rlim_max) {
                fail("prlimit64", "reported a current limit above the maximum");
            }

            unsigned char cpu_mask[16];
            memset(cpu_mask, 0, sizeof(cpu_mask));
            long mask_size = syscall(LTP_NR_SCHED_GETAFFINITY, 0, sizeof(cpu_mask), cpu_mask);
            if (mask_size <= 0) {
                fail("sched_getaffinity", "did not return a positive mask size");
            } else if ((cpu_mask[0] & 1) == 0) {
                fail("sched_getaffinity", "did not report CPU zero");
            }

            struct timespec no_sleep = {0, 0};
            if (syscall(LTP_NR_NANOSLEEP, &no_sleep, 0) != 0) {
                fail("nanosleep", "did not accept a zero-length sleep");
            }
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

        static void test_file_syscalls(void) {
            const char *directory_path = "/ltp-smoke";
            const char *file_path = "/ltp-smoke/data.txt";
            const char *renamed_path = "/ltp-smoke/renamed.txt";
            const char *renamed_again_path = "/ltp-smoke/renamed-again.txt";

            if (syscall(LTP_NR_MKDIRAT, AT_FDCWD, directory_path, 0700) != 0) {
                fail("mkdirat", "did not create the smoke directory");
                return;
            }

            long fd = syscall(LTP_NR_OPENAT, AT_FDCWD, file_path, O_CREAT | O_RDWR | O_TRUNC, 0600);
            if (fd < 0) {
                fail("openat", "did not create the smoke file");
                return;
            }

            if (syscall(LTP_NR_FCNTL, fd, F_GETFD, 0) < 0) {
                fail("fcntl", "did not accept F_GETFD");
            }

            long duplicate_fd = syscall(LTP_NR_DUP, fd);
            if (duplicate_fd < 0) {
                fail("dup", "did not duplicate the smoke file descriptor");
            } else if (syscall(LTP_NR_CLOSE, duplicate_fd) != 0) {
                fail("close", "did not close the duplicated descriptor");
            }

            long fixed_duplicate_fd = syscall(LTP_NR_DUP3, fd, 100, O_CLOEXEC);
            if (fixed_duplicate_fd != 100) {
                fail("dup3", "did not duplicate to the requested descriptor");
            } else if (syscall(LTP_NR_CLOSE, fixed_duplicate_fd) != 0) {
                fail("close", "did not close the dup3 descriptor");
            }

            if (syscall(LTP_NR_WRITE, fd, "abc", 3) != 3) {
                fail("write", "did not write the expected byte count");
            }

            struct iovec write_vectors[2];
            write_vectors[0].iov_base = (void *) "de";
            write_vectors[0].iov_len = 2;
            write_vectors[1].iov_base = (void *) "fg\n";
            write_vectors[1].iov_len = 3;
            if (syscall(LTP_NR_WRITEV, fd, write_vectors, 2) != 5) {
                fail("writev", "did not write the expected vector byte count");
            }

            if (syscall(LTP_NR_PWRITE64, fd, "XY", 2, 1) != 2) {
                fail("pwrite64", "did not write at the requested offset");
            }

            char offset_buffer[3] = {0, 0, 0};
            if (syscall(LTP_NR_PREAD64, fd, offset_buffer, 2, 1) != 2 || strcmp(offset_buffer, "XY") != 0) {
                fail("pread64", "did not read from the requested offset");
            }

            if (syscall(LTP_NR_LSEEK, fd, 0, SEEK_SET) != 0) {
                fail("lseek", "did not seek to the start of the file");
            }

            char direct_buffer[9];
            memset(direct_buffer, 0, sizeof(direct_buffer));
            if (syscall(LTP_NR_READ, fd, direct_buffer, 8) != 8 || strcmp(direct_buffer, "aXYdefg\n") != 0) {
                fail("read", "did not read the expected file contents");
            }

            if (syscall(LTP_NR_LSEEK, fd, 0, SEEK_SET) != 0) {
                fail("lseek", "did not reset the file offset before readv");
            }

            char first_part[4] = {0, 0, 0, 0};
            char second_part[6] = {0, 0, 0, 0, 0, 0};
            struct iovec read_vectors[2];
            read_vectors[0].iov_base = first_part;
            read_vectors[0].iov_len = 3;
            read_vectors[1].iov_base = second_part;
            read_vectors[1].iov_len = 5;
            if (syscall(LTP_NR_READV, fd, read_vectors, 2) != 8
                    || strcmp(first_part, "aXY") != 0
                    || strcmp(second_part, "defg\n") != 0) {
                fail("readv", "did not read the expected vector contents");
            }

            struct stat status;
            if (syscall(LTP_NR_FSTAT, fd, &status) != 0) {
                fail("fstat", "did not return zero");
            } else if (status.st_size != 8) {
                fail("fstat", "did not report the expected file size");
            }

            if (syscall(LTP_NR_FTRUNCATE, fd, 4) != 0) {
                fail("ftruncate", "did not truncate the smoke file");
            }

            if (syscall(LTP_NR_CLOSE, fd) != 0) {
                fail("close", "did not close the smoke file descriptor");
            }

            memset(&status, 0, sizeof(status));
            if (syscall(LTP_NR_NEWFSTATAT, AT_FDCWD, file_path, &status, 0) != 0) {
                fail("newfstatat", "did not stat the smoke file");
            } else if (status.st_size != 4) {
                fail("newfstatat", "did not report the truncated file size");
            }

            if (syscall(LTP_NR_FACCESSAT, AT_FDCWD, file_path, R_OK | W_OK, 0) != 0) {
                fail("faccessat", "did not report read-write access");
            }
            if (syscall(LTP_NR_FACCESSAT2, AT_FDCWD, file_path, R_OK | W_OK, 0) != 0) {
                fail("faccessat2", "did not report read-write access");
            }

            if (syscall(LTP_NR_RENAMEAT, AT_FDCWD, file_path, AT_FDCWD, renamed_path) != 0) {
                fail("renameat", "did not rename the smoke file");
            }
            if (syscall(LTP_NR_RENAMEAT2, AT_FDCWD, renamed_path, AT_FDCWD, renamed_again_path, 0) != 0) {
                fail("renameat2", "did not rename the smoke file with zero flags");
            }
            if (syscall(LTP_NR_UNLINKAT, AT_FDCWD, renamed_again_path, 0) != 0) {
                fail("unlinkat", "did not unlink the smoke file");
            }
            if (syscall(LTP_NR_UNLINKAT, AT_FDCWD, directory_path, AT_REMOVEDIR) != 0) {
                fail("unlinkat", "did not remove the smoke directory");
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
            test_process_resource_syscalls();
            test_getcwd();
            test_uname();
            test_file_syscalls();
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

        val root = ltpSyscallSmokeRoot.get().asFile
        delete(root)
        if (!root.mkdirs()) {
            throw GradleException("Failed to create LTP syscall smoke root mount: $root")
        }

        setArgs(listOf("--mount", "type=bind,src=${root.absolutePath},dst=/", ltpSyscallSmokeElf.get().asFile.absolutePath))
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

        val smokeDirectory = ltpSyscallSmokeRoot.get().file("ltp-smoke").asFile
        if (smokeDirectory.exists()) {
            throw GradleException("LTP syscall smoke left work directory behind: $smokeDirectory")
        }
    }
}

tasks.register("ltpSyscallCheck") {
    group = "verification"
    description = "Generates LTP syscall coverage and runs the LTP ABI-table driven syscall smoke test."

    dependsOn("ltpSyscallCoverage", "testLtpSyscallSmoke")
}
