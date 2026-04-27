import java.time.Duration
import java.io.File

plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "9.4.1"
}

group = "org.glavo"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val mainClassName = "org.glavo.riscv.Main"

application {
    applicationName = "graalriscv"
    mainClass = "org.glavo.riscv.Main"

    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow"
    )
}

tasks.shadowJar {
    manifest.attributes(
        "Main-Class" to mainClassName,
        "Enable-Native-Access" to "ALL-UNNAMED",
    )
}

dependencies {
    implementation("org.graalvm.truffle:truffle-api:25.0.2")
    implementation("net.fornwall:jelf:0.11.0")
    runtimeOnly("org.graalvm.truffle:truffle-runtime:25.0.2")

    compileOnly("org.jetbrains:annotations:26.1.0")
    testCompileOnly("org.jetbrains:annotations:26.1.0")

    annotationProcessor("org.graalvm.truffle:truffle-dsl-processor:25.0.2")
    testAnnotationProcessor("org.graalvm.truffle:truffle-dsl-processor:25.0.2")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.graalvm.polyglot:polyglot:25.0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    timeout.set(Duration.ofMinutes(10))
}

fun findExecutable(command: String): String? {
    val direct = file(command)
    if (direct.isFile) {
        return direct.absolutePath
    }

    val path = System.getenv("PATH") ?: return null
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val extensions = if (isWindows) {
        listOf("") + (System.getenv("PATHEXT") ?: ".EXE;.BAT;.CMD")
            .split(';')
            .filter { it.isNotBlank() }
    } else {
        listOf("")
    }

    return path.split(File.pathSeparator)
        .asSequence()
        .flatMap { directory ->
            extensions.asSequence().map { extension ->
                val fileName = if (extension.isNotEmpty() && command.endsWith(extension, ignoreCase = true)) {
                    command
                } else {
                    command + extension
                }
                File(directory, fileName)
            }
        }
        .firstOrNull { it.isFile }
        ?.absolutePath
}

val riscvGcc = providers.gradleProperty("riscvGcc").orElse("riscv64-unknown-elf-gcc")
val helloWorldExampleElf = layout.buildDirectory.file("examples/hello/hello.elf")

tasks.register<Exec>("buildHelloWorldExample") {
    group = "verification"
    description = "Builds examples/hello/HelloWorld.c with a RISC-V bare-metal GCC toolchain when available."

    inputs.files("examples/hello/HelloWorld.c", "examples/hello/linker.ld")
    outputs.file(helloWorldExampleElf)

    onlyIf {
        val compiler = findExecutable(riscvGcc.get())
        if (compiler == null) {
            logger.lifecycle("Skipping buildHelloWorldExample: ${riscvGcc.get()} was not found in PATH.")
        }
        compiler != null
    }

    doFirst {
        val compiler = findExecutable(riscvGcc.get())
            ?: throw GradleException("RISC-V GCC was not found: ${riscvGcc.get()}")
        helloWorldExampleElf.get().asFile.parentFile.mkdirs()
        executable = compiler
        args(
            "-march=rv64imac",
            "-mabi=lp64",
            "-mcmodel=medany",
            "-nostdlib",
            "-nostartfiles",
            "-ffreestanding",
            "-fno-builtin",
            "-fno-pic",
            "-fno-pie",
            "-fno-stack-protector",
            "-fno-asynchronous-unwind-tables",
            "-Wl,-T,${layout.projectDirectory.file("examples/hello/linker.ld").asFile.absolutePath}",
            "-Wl,--no-relax",
            "-Wl,--build-id=none",
            "-o",
            helloWorldExampleElf.get().asFile.absolutePath,
            layout.projectDirectory.file("examples/hello/HelloWorld.c").asFile.absolutePath
        )
    }
}

tasks.register<JavaExec>("runHelloWorldExample") {
    group = "verification"
    description = "Runs the compiled Hello World RISC-V example with the GraalRISCV CLI."

    dependsOn("classes", "buildHelloWorldExample")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassName
    jvmArgs(application.applicationDefaultJvmArgs)

    onlyIf {
        val elf = helloWorldExampleElf.get().asFile
        if (!elf.isFile) {
            logger.lifecycle("Skipping runHelloWorldExample: ${elf.absolutePath} does not exist.")
        }
        elf.isFile
    }

    doFirst {
        args(helloWorldExampleElf.get().asFile.absolutePath)
    }
}
