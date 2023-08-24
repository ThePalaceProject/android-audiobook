import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.util.Properties
import java.io.FileOutputStream

dependencies {
    implementation(project(":org.librarysimplified.audiobook.http"))
    implementation(project(":org.librarysimplified.audiobook.json_canon"))
    implementation(project(":org.librarysimplified.audiobook.json_web_token"))
    implementation(project(":org.librarysimplified.audiobook.lcp.license_status"))
    implementation(project(":org.librarysimplified.audiobook.license_check.spi"))
    implementation(project(":org.librarysimplified.audiobook.manifest_parser.extension_spi"))
    implementation(project(":org.librarysimplified.audiobook.manifest_parser.webpub"))

    implementation(libs.irradia.fieldrush.api)
    implementation(libs.irradia.fieldrush.vanilla)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.okhttp3)
    implementation(libs.slf4j)
}

/*
 * Generate a properties file based on various settings.
 */

project.task("GeneratePropertiesResources") {
    val directory = File(
        project.projectDir,
        "src/main/resources/org/librarysimplified/audiobook/feedbooks"
    ).absoluteFile
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
