import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.io.FileOutputStream
import java.util.Properties

dependencies {
    implementation(project(":org.librarysimplified.audiobook.api"))
    implementation(project(":org.librarysimplified.audiobook.manifest.api"))

    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.extractor)
    implementation(libs.media3.session)
    implementation(libs.palace.http.api)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)

    compileOnly(libs.jcip)
}

/*
 * Generate a properties file based on various settings.
 */

project.task("GeneratePropertiesResources") {
    val directory = File(project.projectDir, "src/main/resources/org/librarysimplified/audiobook/open_access").absoluteFile
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
