dependencies {
  api(project(":org.librarysimplified.audiobook.api"))

  api(libs.irradia.fieldrush.api)

  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlin.reflect)
  implementation(libs.slf4j)

  compileOnly(libs.jcip)
}
