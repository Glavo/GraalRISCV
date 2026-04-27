repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.1.0")

    implementation("de.undercouch:gradle-download-task:5.7.0")
    implementation("org.tukaani:xz:1.12")

    val kalaCompressVersion = "1.27.1-3"
    implementation("org.glavo.kala:kala-compress-archivers-tar:$kalaCompressVersion")
    implementation("org.glavo.kala:kala-compress-archivers-zip:$kalaCompressVersion")
}
