dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    api(project(":org.librarysimplified.audiobook.manifest_fulfill.spi"))

    implementation(libs.slf4j)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)

    compileOnly(libs.jcip)
}
