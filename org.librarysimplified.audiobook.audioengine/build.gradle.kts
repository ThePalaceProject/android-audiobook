import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.io.FileOutputStream
import java.util.Properties

dependencies {
    implementation(project(":org.librarysimplified.audiobook.api"))
    implementation(project(":org.librarysimplified.audiobook.manifest.api"))
    implementation(project(":org.librarysimplified.audiobook.parser.api"))

    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.joda.time)
    implementation(libs.kabstand)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.palace.http.api)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)

    // Findaway transitive dependencies.
    implementation(libs.dagger)
    implementation(libs.exoplayer2.core)
    implementation(libs.findaway)
    implementation(libs.findaway.common)
    implementation(libs.findaway.listening)
    implementation(libs.findaway.persistence)
    implementation(libs.findaway.play.android)
    implementation(libs.google.gson)
    implementation(libs.javax.inject)
    implementation(libs.koin.android)
    implementation(libs.koin.core)
    implementation(libs.koin.core.jvm)
    implementation(libs.moshi)
    implementation(libs.moshi.adapters)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp3)
    implementation(libs.okhttp3.logging.interceptor)
    implementation(libs.retrofit2)
    implementation(libs.retrofit2.adapter.rxjava)
    implementation(libs.retrofit2.converter.gson)
    implementation(libs.retrofit2.converter.moshi)
    implementation(libs.rxandroid)
    implementation(libs.rxjava)
    implementation(libs.rxrelay)
    implementation(libs.sqlbrite)
    implementation(libs.stately.common)
    implementation(libs.stately.concurrency)
    implementation(libs.timber)

    compileOnly(libs.jcip)
}

/*
 * Generate a properties file based on various settings.
 */

project.task("GeneratePropertiesResources") {
    val directory = File(project.projectDir, "src/main/resources/org/librarysimplified/audiobook/audioengine").absoluteFile
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
