dependencies {
  api(project(":org.librarysimplified.audiobook.api"))
  api(project(":org.librarysimplified.audiobook.manifest_parser.extension_spi"))
  api(project(":org.librarysimplified.audiobook.parser.api"))

  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlin.reflect)
  implementation(libs.slf4j)

  compileOnly(libs.jcip)
}
