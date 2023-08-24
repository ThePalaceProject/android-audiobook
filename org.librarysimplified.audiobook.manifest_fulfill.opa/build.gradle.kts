dependencies {
  api(project(":org.librarysimplified.audiobook.manifest_fulfill.spi"))

  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlin.reflect)
  implementation(libs.okhttp3)
  implementation(libs.slf4j)

  compileOnly(libs.jcip)
}