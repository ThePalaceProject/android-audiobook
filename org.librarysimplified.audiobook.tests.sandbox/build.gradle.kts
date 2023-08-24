dependencies {
  implementation(project(":org.librarysimplified.audiobook.api"))
  implementation(project(":org.librarysimplified.audiobook.manifest_parser.webpub"))
  implementation(project(":org.librarysimplified.audiobook.mocking"))
  implementation(project(":org.librarysimplified.audiobook.open_access"))
  implementation(project(":org.librarysimplified.audiobook.views"))

  implementation(libs.androidx.app.compat)
  implementation(libs.junit)
  implementation(libs.kotlin.reflect)
  implementation(libs.kotlin.stdlib)
  implementation(libs.slf4j)
}
