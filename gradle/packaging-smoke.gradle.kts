import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.glavo.RiscVSmokeElfTask

val isWindowsHost = System.getProperty("os.name").lowercase().contains("win")
val smokeElfFile = layout.buildDirectory.file("fixtures/smoke/smoke.elf")
val shadowJarFile = tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }
val javaToolchains = extensions.getByType<JavaToolchainService>()
val javaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
}

fun verifySmokeOutput(taskName: String, stdout: ByteArrayOutputStream, stderr: ByteArrayOutputStream) {
    val actualOutput = stdout.toString(StandardCharsets.UTF_8)
    if (actualOutput != "Smoke OK\n") {
        throw GradleException("$taskName produced unexpected stdout: ${actualOutput.trim()}")
    }

    val actualError = stderr.toString(StandardCharsets.UTF_8)
    if (actualError.isNotEmpty()) {
        throw GradleException("$taskName produced unexpected stderr: $actualError")
    }
}

tasks.register<RiscVSmokeElfTask>("generateSmokeElf") {
    group = "verification"
    description = "Generates a tiny RISC-V ELF fixture for package smoke tests."

    outputFile.set(smokeElfFile)
}

tasks.register<Exec>("runInstallDistSmoke") {
    group = "verification"
    description = "Runs the generated smoke ELF through the installDist launch script."

    dependsOn("installDist", "generateSmokeElf")
    inputs.file(smokeElfFile)

    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    standardOutput = stdout
    errorOutput = stderr

    doFirst {
        stdout.reset()
        stderr.reset()

        val scriptName = if (isWindowsHost) "graalriscv.bat" else "graalriscv"
        val script = layout.buildDirectory.file("install/graalriscv/bin/$scriptName").get().asFile
        val elf = smokeElfFile.get().asFile
        if (isWindowsHost) {
            commandLine("cmd", "/c", script.absolutePath, elf.absolutePath)
        } else {
            commandLine(script.absolutePath, elf.absolutePath)
        }
    }

    doLast {
        verifySmokeOutput(name, stdout, stderr)
    }
}

tasks.register<Exec>("runShadowJarSmoke") {
    group = "verification"
    description = "Runs the generated smoke ELF through the packaged Shadow JAR."

    dependsOn("shadowJar", "generateSmokeElf")
    inputs.file(smokeElfFile)
    inputs.file(shadowJarFile)

    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    standardOutput = stdout
    errorOutput = stderr

    doFirst {
        stdout.reset()
        stderr.reset()

        commandLine(
            javaLauncher.get().executablePath.asFile.absolutePath,
            "--enable-native-access=ALL-UNNAMED",
            "--sun-misc-unsafe-memory-access=allow",
            "-jar",
            shadowJarFile.get().asFile.absolutePath,
            smokeElfFile.get().asFile.absolutePath
        )
    }

    doLast {
        verifySmokeOutput(name, stdout, stderr)
    }
}

tasks.register("packageSmokeTest") {
    group = "verification"
    description = "Runs package smoke checks that do not require an external RISC-V toolchain."

    dependsOn("runInstallDistSmoke", "runShadowJarSmoke")
}

tasks.register("ciCheck") {
    group = "verification"
    description = "Compiles, tests, packages, and runs no-toolchain package smoke checks."

    dependsOn(
        "compileJava",
        "compileTestJava",
        "test",
        "distZip",
        "distTar",
        "shadowJar",
        "packageSmokeTest"
    )
}
