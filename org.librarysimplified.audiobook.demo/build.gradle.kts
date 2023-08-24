import com.android.build.api.dsl.ApplicationExtension

dependencies {
  implementation(project(":org.librarysimplified.audiobook.downloads"))
  implementation(project(":org.librarysimplified.audiobook.feedbooks"))
  implementation(project(":org.librarysimplified.audiobook.http"))
  implementation(project(":org.librarysimplified.audiobook.json_canon"))
  implementation(project(":org.librarysimplified.audiobook.json_web_token"))
  implementation(project(":org.librarysimplified.audiobook.lcp.license_status"))
  implementation(project(":org.librarysimplified.audiobook.license_check.api"))
  implementation(project(":org.librarysimplified.audiobook.license_check.spi"))
  implementation(project(":org.librarysimplified.audiobook.manifest.api"))
  implementation(project(":org.librarysimplified.audiobook.manifest_fulfill.api"))
  implementation(project(":org.librarysimplified.audiobook.manifest_fulfill.basic"))
  implementation(project(":org.librarysimplified.audiobook.manifest_fulfill.opa"))
  implementation(project(":org.librarysimplified.audiobook.manifest_fulfill.spi"))
  implementation(project(":org.librarysimplified.audiobook.manifest_parser.api"))
  implementation(project(":org.librarysimplified.audiobook.manifest_parser.extension_spi"))
  implementation(project(":org.librarysimplified.audiobook.manifest_parser.webpub"))
  implementation(project(":org.librarysimplified.audiobook.open_access"))
  implementation(project(":org.librarysimplified.audiobook.parser.api"))
  implementation(project(":org.librarysimplified.audiobook.rbdigital"))
  implementation(project(":org.librarysimplified.audiobook.views"))

  implementation(libs.androidx.constraint.layout)
  implementation(libs.androidx.core)
  implementation(libs.androidx.app.compat)
  implementation(libs.androidx.recycler.view)
  implementation(libs.kotlin.stdlib)
  implementation(libs.okhttp3)
  implementation(libs.palace.http.api)
  implementation(libs.palace.http.vanilla)
  implementation(libs.slf4j)
  implementation(libs.logback.android)

  if (project.hasProperty("org.thepalaceproject.audiobook.demo.with_findaway")) {
    if (project.property("org.thepalaceproject.audiobook.demo.with_findaway") == "true") {
      implementation(libs.palace.findaway)
    }
  }

  compileOnly(libs.jcip)
}

android {
  defaultConfig {
    versionName = project.version as String
    versionCode = 1000
  }
}
