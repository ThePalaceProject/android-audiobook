dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.slf4j)
    implementation(libs.joda.time)

    compileOnly(libs.jcip)
}
