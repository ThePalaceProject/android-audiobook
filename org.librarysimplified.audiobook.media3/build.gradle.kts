import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.io.FileOutputStream
import java.util.Properties

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":org.librarysimplified.audiobook.api"))
    implementation(project(":org.librarysimplified.audiobook.lcp.downloads"))
    implementation(project(":org.librarysimplified.audiobook.manifest.api"))
    implementation(project(":org.librarysimplified.audiobook.manifest_fulfill.spi"))

    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.constraintlayout.core)
    implementation(libs.androidx.constraintlayout.solver)
    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.joda.time)
    implementation(libs.kabstand)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.extractor)
    implementation(libs.media3.session)
    implementation(libs.palace.http.api)
    implementation(libs.r2.lcp)
    implementation(libs.r2.shared)
    implementation(libs.r2.streamer)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)

    compileOnly(libs.jcip)
}

/*
 * Generate a properties file based on various settings.
 */

project.task("GeneratePropertiesResources") {
    val directory = File(project.projectDir, "src/main/resources/org/librarysimplified/audiobook/media3").absoluteFile
    directory.mkdirs()
    val file = File(directory, "provider.properties")
    file.createNewFile()

    val properties = Properties()
    val versionName: String = project.version as String
    val major = versionName.split(".")[0]
    val minor = versionName.split(".")[1]
    val patch = versionName.split(".")[2]
    properties.setProperty("version.major", major)
    properties.setProperty("version.minor", minor)
    properties.setProperty("version.patch", patch)
    properties.store(FileOutputStream(file), "Automatically generated - DO NOT EDIT")
}

project.tasks.named("preBuild")
    .dependsOn("GeneratePropertiesResources")
