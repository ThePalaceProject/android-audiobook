dependencies {
  api(project(":org.librarysimplified.audiobook.api"))

  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlin.reflect)
  implementation(libs.okhttp3)
  implementation(libs.slf4j)
}