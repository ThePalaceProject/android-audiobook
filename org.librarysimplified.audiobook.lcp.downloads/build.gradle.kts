val lcpDRM =
    project.findProperty("org.thepalaceproject.lcp.enabled") == "true"

dependencies {
    implementation(project(":org.librarysimplified.audiobook.api"))
    implementation(project(":org.librarysimplified.audiobook.manifest_fulfill.basic"))
    implementation(project(":org.librarysimplified.audiobook.manifest_fulfill.spi"))

    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.constraintlayout.core)
    implementation(libs.androidx.constraintlayout.solver)
    implementation(libs.irradia.mime.api)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core.jvm)
    implementation(libs.palace.http.api)
    implementation(libs.palace.http.downloads)
    implementation(libs.palace.http.vanilla)
    implementation(libs.r2.lcp)
    implementation(libs.r2.shared)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)

    if (lcpDRM) {
        implementation(libs.readium.lcp) {
            artifact {
                type = "aar"
            }
        }
    }
}
