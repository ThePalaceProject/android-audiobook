dependencies {
    implementation(project(":org.librarysimplified.audiobook.api"))
    implementation(project(":org.librarysimplified.audiobook.manifest.api"))
    implementation(project(":org.librarysimplified.audiobook.manifest_parser.api"))
    implementation(project(":org.librarysimplified.audiobook.manifest_parser.extension_spi"))
    implementation(project(":org.librarysimplified.audiobook.parser.api"))

    implementation(libs.irradia.fieldrush.api)
    implementation(libs.irradia.fieldrush.vanilla)
    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j)

    compileOnly(libs.jcip)
}
