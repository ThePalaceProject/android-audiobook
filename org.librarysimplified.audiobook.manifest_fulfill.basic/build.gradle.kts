dependencies {
    implementation(project(":org.librarysimplified.audiobook.api"))
    implementation(project(":org.librarysimplified.audiobook.http"))
    implementation(project(":org.librarysimplified.audiobook.manifest_fulfill.spi"))

    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.okhttp3)
    implementation(libs.palace.http.api)
    implementation(libs.rxjava)
    implementation(libs.slf4j)

    compileOnly(libs.jcip)
}
