dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":org.librarysimplified.audiobook.api"))
    implementation(project(":org.librarysimplified.audiobook.http"))
    implementation(project(":org.librarysimplified.audiobook.manifest_fulfill.spi"))
    implementation(project(":org.librarysimplified.audiobook.manifest.api"))

    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.api)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)

    compileOnly(libs.jcip)
}
