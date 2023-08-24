dependencies {
  api(project(":org.librarysimplified.audiobook.manifest_parser.api"))

  implementation(libs.irradia.fieldrush.api)
  implementation(libs.irradia.fieldrush.vanilla)
  implementation(libs.joda.time)
  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlin.reflect)
  implementation(libs.slf4j)

  compileOnly(libs.jcip)
}