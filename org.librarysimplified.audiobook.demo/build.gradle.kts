dependencies {
    implementation(project(":org.librarysimplified.audiobook.api"))
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

    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.livedata.core)
    implementation(libs.androidx.lifecycle.livedata.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recycler.view)
    implementation(libs.androidx.savedstate)
    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.google.material)
    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.kotlin.stdlib)
    implementation(libs.logback.android)
    implementation(libs.nypl.theme)
    implementation(libs.okhttp3)
    implementation(libs.palace.http.api)
    implementation(libs.palace.http.vanilla)
    implementation(libs.rxjava)
    implementation(libs.slf4j)

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
