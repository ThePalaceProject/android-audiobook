dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    api(libs.okhttp3)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.slf4j)

    compileOnly(libs.jcip)
}
