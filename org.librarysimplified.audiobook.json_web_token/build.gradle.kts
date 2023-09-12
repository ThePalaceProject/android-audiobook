dependencies {
    implementation(project(":org.librarysimplified.audiobook.parser.api"))
    implementation(project(":org.librarysimplified.audiobook.json_canon"))

    implementation(libs.irradia.fieldrush.api)
    implementation(libs.irradia.fieldrush.vanilla)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j)

    compileOnly(libs.jcip)
}
