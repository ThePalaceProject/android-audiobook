dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.irradia.mime.api)
    implementation(libs.joda.time)
    implementation(libs.kabstand)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j)

    compileOnly(libs.jcip)
}
