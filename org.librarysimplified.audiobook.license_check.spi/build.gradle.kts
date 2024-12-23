dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    api(project(":org.librarysimplified.audiobook.manifest.api"))
    api(project(":org.librarysimplified.audiobook.api"))

    api(libs.irradia.mime.api)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.slf4j)

    compileOnly(libs.jcip)
}
