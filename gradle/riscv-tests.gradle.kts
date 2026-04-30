import org.gradle.api.file.RelativePath
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.SourceSetContainer
import org.glavo.riscv.tests.RiscVTestsBuildTask
import org.glavo.riscv.tests.RiscVTestsRunTask
import org.glavo.zig.ZigToolchainSupport

val applicationExtension = extensions.getByType<JavaApplication>()
val sourceSets = extensions.getByType<SourceSetContainer>()
val zigToolchain = ZigToolchainSupport.getOrCreate(project)

val mainClassName = applicationExtension.mainClass.get()
val applicationDefaultJvmArgs = applicationExtension.applicationDefaultJvmArgs.toList()

fun RiscVTestsBuildTask.configureZigExecutable() {
    zigToolchain.configureExecutable(this, zigExecutable)
}

val riscVTestsRevision = "0bbecd1a01c61a16ad45fdfd89f29ebfdb493d1d"
val riscVTestsArchiveRoot = "riscv-tests-$riscVTestsRevision"
val riscVTestsArchiveFile = layout.buildDirectory.file("downloads/riscv-tests/riscv-tests-$riscVTestsRevision.zip")
val riscVTestsSourceDirectory = layout.buildDirectory.dir("downloads/riscv-tests/$riscVTestsRevision")
val riscVTestEnvRevision = "6de71edb142be36319e380ce782c3d1830c65d68"
val riscVTestEnvArchiveRoot = "riscv-test-env-$riscVTestEnvRevision"
val riscVTestEnvArchiveFile =
    layout.buildDirectory.file("downloads/riscv-test-env/riscv-test-env-$riscVTestEnvRevision.zip")
val riscVTestEnvSourceDirectory = layout.buildDirectory.dir("downloads/riscv-test-env/$riscVTestEnvRevision")
val riscVTestsElfDirectory = layout.buildDirectory.dir("riscv-tests/rv64gc-p")
val rva22AcceptanceSourceInputDirectory = layout.projectDirectory.dir("src/test/asm/rva22")
val rva22AcceptanceGeneratedSourceDirectory = layout.buildDirectory.dir("generated/rva22-acceptance")
val rva22AcceptanceElfDirectory = layout.buildDirectory.dir("riscv-tests/rva22-acceptance")
val riscVTestsMaxInstructions = providers.gradleProperty("graalriscv.riscvTestsMaxInstructions")
    .orElse("10000000")
val riscVTestsFilter = providers.gradleProperty("graalriscv.riscvTestsFilter")
    .orElse(".*")
val rva22AcceptanceFilter = providers.gradleProperty("graalriscv.rva22AcceptanceFilter")
    .orElse(".*")
val riscVTestsRequiredPaths = listOf(
    "configure.ac",
    "isa",
    "benchmarks"
)
val riscVTestEnvRequiredPaths = listOf(
    "p/link.ld",
    "p/riscv_test.h"
)

val downloadRiscVTestsArchive by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    group = "build setup"
    description = "Downloads riscv-tests source archive revision $riscVTestsRevision."

    src("https://github.com/riscv-software-src/riscv-tests/archive/$riscVTestsRevision.zip")
    dest(riscVTestsArchiveFile.get().asFile)
    overwrite(false)
    tempAndMove(true)
    retries(3)
    connectTimeout(30_000)
    readTimeout(30_000)

    doFirst {
        val archive = riscVTestsArchiveFile.get().asFile
        val parent = archive.parentFile
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw GradleException("Failed to create riscv-tests archive directory: $parent")
        }
        if (archive.isFile && archive.length() == 0L) {
            delete(archive)
        }
    }
}

tasks.register<Sync>("extractRiscVTestsSources") {
    group = "build setup"
    description = "Extracts the pinned riscv-tests source archive."

    dependsOn(downloadRiscVTestsArchive)
    from(zipTree(riscVTestsArchiveFile.get().asFile)) {
        include("$riscVTestsArchiveRoot/**")
        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
        includeEmptyDirs = false
    }
    into(riscVTestsSourceDirectory)

    doLast {
        val sourceDirectory = riscVTestsSourceDirectory.get().asFile
        val missingPaths = riscVTestsRequiredPaths
            .map { sourceDirectory.resolve(it) }
            .filterNot { it.exists() }
        if (missingPaths.isNotEmpty()) {
            throw GradleException("riscv-tests archive did not contain expected paths: ${missingPaths.joinToString()}")
        }
    }
}

val downloadRiscVTestEnvArchive by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    group = "build setup"
    description = "Downloads riscv-test-env source archive revision $riscVTestEnvRevision."

    src("https://github.com/riscv/riscv-test-env/archive/$riscVTestEnvRevision.zip")
    dest(riscVTestEnvArchiveFile.get().asFile)
    overwrite(false)
    tempAndMove(true)
    retries(3)
    connectTimeout(30_000)
    readTimeout(30_000)

    doFirst {
        val archive = riscVTestEnvArchiveFile.get().asFile
        val parent = archive.parentFile
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw GradleException("Failed to create riscv-test-env archive directory: $parent")
        }
        if (archive.isFile && archive.length() == 0L) {
            delete(archive)
        }
    }
}

tasks.register<Sync>("extractRiscVTestEnvSources") {
    group = "build setup"
    description = "Extracts the pinned riscv-test-env source archive."

    dependsOn(downloadRiscVTestEnvArchive)
    from(zipTree(riscVTestEnvArchiveFile.get().asFile)) {
        include("$riscVTestEnvArchiveRoot/**")
        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
        includeEmptyDirs = false
    }
    into(riscVTestEnvSourceDirectory)

    doLast {
        val sourceDirectory = riscVTestEnvSourceDirectory.get().asFile
        val missingPaths = riscVTestEnvRequiredPaths
            .map { sourceDirectory.resolve(it) }
            .filterNot { it.exists() }
        if (missingPaths.isNotEmpty()) {
            throw GradleException(
                "riscv-test-env archive did not contain expected paths: ${missingPaths.joinToString()}"
            )
        }
    }
}

tasks.register<RiscVTestsBuildTask>("buildRiscVTests") {
    group = "verification"
    description = "Downloads and builds riscv-tests RV64GC p-mode ISA tests without Make."

    dependsOn("extractRiscVTestsSources", "extractRiscVTestEnvSources")
    configureZigExecutable()
    sourceDirectory.set(riscVTestsSourceDirectory)
    environmentDirectory.set(riscVTestEnvSourceDirectory.map { it.dir("p") })
    outputDirectory.set(riscVTestsElfDirectory)
    localCacheDirectory.set(layout.buildDirectory.dir("zig-local-cache"))
    globalCacheDirectory.set(layout.buildDirectory.dir("zig-global-cache"))
}

tasks.register<Sync>("prepareRva22AcceptanceSources") {
    group = "build setup"
    description = "Prepares repository-owned RVA22U64 acceptance sources for the riscv-tests builder."

    dependsOn("extractRiscVTestsSources")
    from(rva22AcceptanceSourceInputDirectory) {
        into("isa/rva22")
    }
    from(riscVTestsSourceDirectory.map { it.dir("isa/macros/scalar") }) {
        into("isa/macros/scalar")
    }
    into(rva22AcceptanceGeneratedSourceDirectory)
}

tasks.register<RiscVTestsBuildTask>("buildRva22AcceptanceTests") {
    group = "verification"
    description = "Builds repository-owned RVA22U64 acceptance ELFs without Make."

    dependsOn("prepareRva22AcceptanceSources", "extractRiscVTestEnvSources")
    configureZigExecutable()
    sourceDirectory.set(rva22AcceptanceGeneratedSourceDirectory)
    environmentDirectory.set(riscVTestEnvSourceDirectory.map { it.dir("p") })
    outputDirectory.set(rva22AcceptanceElfDirectory)
    localCacheDirectory.set(layout.buildDirectory.dir("zig-local-cache"))
    globalCacheDirectory.set(layout.buildDirectory.dir("zig-global-cache"))
    suites.set(listOf("rva22"))
    enabledTargetFeatures.set(listOf(
        "m",
        "a",
        "f",
        "d",
        "c",
        "zicsr",
        "zifencei",
        "zba",
        "zbb",
        "zbs",
        "zfhmin",
        "zkt",
        "zfa",
    ))
}

tasks.register<RiscVTestsRunTask>("testRiscVTests") {
    group = "verification"
    description = "Builds and runs the RV64GC p-mode riscv-tests ISA ELFs with the GraalRISCV CLI."

    dependsOn("classes", "buildRiscVTests")
    elfDirectory.set(riscVTestsElfDirectory)
    classpath.from(sourceSets.named("main").get().runtimeClasspath)
    mainClass.set(mainClassName)
    jvmArguments.set(applicationDefaultJvmArgs)
    maxInstructions.set(riscVTestsMaxInstructions)
    filter.set(riscVTestsFilter)
}

tasks.register<RiscVTestsRunTask>("testRva22Acceptance") {
    group = "verification"
    description = "Builds and runs repository-owned RVA22U64 acceptance ELFs with the GraalRISCV CLI."

    dependsOn("classes", "buildRva22AcceptanceTests")
    elfDirectory.set(rva22AcceptanceElfDirectory)
    classpath.from(sourceSets.named("main").get().runtimeClasspath)
    mainClass.set(mainClassName)
    jvmArguments.set(applicationDefaultJvmArgs)
    maxInstructions.set(riscVTestsMaxInstructions)
    filter.set(rva22AcceptanceFilter)
}
