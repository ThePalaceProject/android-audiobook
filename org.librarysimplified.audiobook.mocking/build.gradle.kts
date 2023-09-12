dependencies {
    implementation(project(":org.librarysimplified.audiobook.api"))
    implementation(project(":org.librarysimplified.audiobook.manifest.api"))

    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.rxjava)
    implementation(libs.slf4j)

    compileOnly(libs.jcip)
}
