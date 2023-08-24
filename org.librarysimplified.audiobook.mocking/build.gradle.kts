dependencies {
  api(project(":org.librarysimplified.audiobook.api"))

  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlin.reflect)
  implementation(libs.slf4j)
  implementation(libs.joda.time)

  compileOnly(libs.jcip)
}
