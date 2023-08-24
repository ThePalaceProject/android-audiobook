dependencies {
  api(project(":org.librarysimplified.audiobook.manifest.api"))
  api(project(":org.librarysimplified.audiobook.license_check.spi"))

  api(libs.rxjava)

  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlin.reflect)
  implementation(libs.slf4j)

  compileOnly(libs.jcip)
}
