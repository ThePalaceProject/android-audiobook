dependencies {
    androidTestImplementation(libs.androidx.app.compat)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlin.stdlib)
    androidTestImplementation(libs.logback.android)
    androidTestImplementation(libs.slf4j)

    androidTestUtil(libs.androidx.test.orchestrator)

    implementation(project(":org.librarysimplified.audiobook.api"))
    implementation(project(":org.librarysimplified.audiobook.manifest_parser.webpub"))
    implementation(project(":org.librarysimplified.audiobook.mocking"))
    implementation(project(":org.librarysimplified.audiobook.open_access"))
    implementation(project(":org.librarysimplified.audiobook.views"))
    implementation(project(":org.librarysimplified.audiobook.tests"))
}

android {
    packagingOptions {
        exclude("META-INF/LICENSE.md")
        exclude("META-INF/LICENSE-notice.md")
    }
}