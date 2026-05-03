import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

val sourceSets = extensions.getByType<SourceSetContainer>()
val javaToolchains = extensions.getByType<JavaToolchainService>()

val nativeImageMainClassName = "org.glavo.riscv.Main"
val nativeImageBaseName = "jriscv"
val nativeImageExecutableName = if (System.getProperty("os.name").lowercase().contains("win")) {
    "$nativeImageBaseName.exe"
} else {
    nativeImageBaseName
}
val nativeImageToolName = if (System.getProperty("os.name").lowercase().contains("win")) {
    "native-image.cmd"
} else {
    "native-image"
}
val nativeImageOutputDirectory = layout.buildDirectory.dir("native/nativeCompile")
val nativeImageTempDirectory = layout.buildDirectory.dir("native/nativeCompile/tmp")
val nativeImageExecutableFile = nativeImageOutputDirectory.map { it.file(nativeImageExecutableName) }
val nativeImageOutputBaseFile = nativeImageOutputDirectory.map { it.file(nativeImageBaseName) }
val nativeImageSmokeElfFile = layout.buildDirectory.file("fixtures/smoke/smoke.elf")
val nativeImageJavaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
}
val nativeImageModuleArgs = listOf(
    "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"
)
val nativeImageBuilderJvmArgs = listOf(
    "-J--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
    "-J--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
)
val configuredNativeImageHome = providers.gradleProperty("nativeImageGraalVmHome")
    .orElse(providers.gradleProperty("graalVmHome"))
    .orElse(providers.environmentVariable("GRAALVM_HOME"))
    .orElse(providers.environmentVariable("JAVA_HOME"))

fun resolveNativeImageExecutable(): File {
    val candidates = ArrayList<File>()
    val configuredHome = configuredNativeImageHome.orNull
    if (configuredHome != null && configuredHome.isNotBlank()) {
        candidates.add(file(configuredHome).resolve("bin").resolve(nativeImageToolName))
    }

    candidates.add(nativeImageJavaLauncher.get().executablePath.asFile.parentFile.resolve(nativeImageToolName))

    return candidates.firstOrNull { it.isFile } ?: throw GradleException(
        "Could not find $nativeImageToolName. Set -PnativeImageGraalVmHome=<graalvm-home> " +
                "or set GRAALVM_HOME/JAVA_HOME to a GraalVM installation with Native Image. " +
                "Checked: ${candidates.joinToString { it.absolutePath }}"
    )
}

fun nativeImageCommandLine(nativeImageExecutable: File, arguments: List<String>): List<String> {
    return if (nativeImageExecutable.extension.equals("cmd", ignoreCase = true)) {
        listOf("cmd", "/c", nativeImageExecutable.absolutePath) + arguments
    } else {
        listOf(nativeImageExecutable.absolutePath) + arguments
    }
}

fun verifyNativeImageSmokeOutput(taskName: String, stdout: ByteArrayOutputStream, stderr: ByteArrayOutputStream) {
    val actualOutput = stdout.toString(StandardCharsets.UTF_8)
    if (actualOutput != "Smoke OK\n") {
        throw GradleException("$taskName produced unexpected stdout: ${actualOutput.trim()}")
    }

    val actualError = stderr.toString(StandardCharsets.UTF_8)
    if (actualError.isNotEmpty()) {
        throw GradleException("$taskName produced unexpected stderr: $actualError")
    }
}

tasks.register<Exec>("nativeCompile") {
    group = "build"
    description = "Builds a native JRISC-V executable with GraalVM native-image."

    dependsOn("classes")
    inputs.files(sourceSets.named("main").get().runtimeClasspath)
    inputs.property("mainClassName", nativeImageMainClassName)
    inputs.property("executableName", nativeImageExecutableName)
    inputs.property("graalVmHome", configuredNativeImageHome.orNull ?: "")
    inputs.property("nativeImageModuleArgs", nativeImageModuleArgs)
    inputs.property("nativeImageBuilderJvmArgs", nativeImageBuilderJvmArgs)
    outputs.file(nativeImageExecutableFile)

    doFirst {
        val outputDirectory = nativeImageOutputDirectory.get().asFile
        val tempDirectory = nativeImageTempDirectory.get().asFile
        outputDirectory.mkdirs()
        tempDirectory.mkdirs()
        delete(nativeImageExecutableFile.get().asFile)
        delete(outputDirectory.resolve("$nativeImageExecutableName.exe"))

        val nativeImageExecutable = resolveNativeImageExecutable()
        val runtimeClasspath = sourceSets.named("main").get().runtimeClasspath.asPath
        val arguments = nativeImageBuilderJvmArgs + nativeImageModuleArgs + listOf(
            "--no-fallback",
            "-o",
            nativeImageOutputBaseFile.get().asFile.absolutePath,
            "-cp",
            runtimeClasspath,
            nativeImageMainClassName
        )

        environment("TMP", tempDirectory.absolutePath)
        environment("TEMP", tempDirectory.absolutePath)
        commandLine(nativeImageCommandLine(nativeImageExecutable, arguments))
    }
}

tasks.register<Exec>("runNativeImageSmoke") {
    group = "verification"
    description = "Runs the generated smoke ELF through the native JRISC-V executable."

    dependsOn("nativeCompile", "generateSmokeElf")
    inputs.file(nativeImageExecutableFile)
    inputs.file(nativeImageSmokeElfFile)

    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    standardOutput = stdout
    errorOutput = stderr

    doFirst {
        stdout.reset()
        stderr.reset()
        commandLine(
            nativeImageExecutableFile.get().asFile.absolutePath,
            nativeImageSmokeElfFile.get().asFile.absolutePath
        )
    }

    doLast {
        verifyNativeImageSmokeOutput(name, stdout, stderr)
    }
}

tasks.register("nativeImageSmokeTest") {
    group = "verification"
    description = "Builds the native executable and runs the no-toolchain smoke test."

    dependsOn("runNativeImageSmoke")
}
