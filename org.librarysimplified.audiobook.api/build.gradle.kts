dependencies {
  api(project(":org.librarysimplified.audiobook.manifest.api"))

  api(libs.google.guava)
  api(libs.jackson.databind)
  api(libs.joda.time)
  api(libs.rxjava)

  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlin.reflect)
  implementation(libs.r2.shared)
  implementation(libs.slf4j)
}