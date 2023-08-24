dependencies {
  api(project(":org.librarysimplified.audiobook.api"))

  implementation(libs.androidx.app.compat)
  implementation(libs.androidx.constraint.layout)
  implementation(libs.androidx.media)
  implementation(libs.androidx.recycler.view)
  implementation(libs.androidx.view.pager2)
  implementation(libs.google.material)
  implementation(libs.kotlin.reflect)
  implementation(libs.kotlin.stdlib)
  implementation(libs.nypl.theme)
  implementation(libs.slf4j)
}