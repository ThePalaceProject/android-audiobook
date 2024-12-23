dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    api(project(":org.librarysimplified.audiobook.license_check.spi"))
    api(project(":org.librarysimplified.audiobook.parser.api"))

    implementation(libs.irradia.fieldrush.api)
    implementation(libs.irradia.fieldrush.vanilla)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.slf4j)

    compileOnly(libs.jcip)
}
