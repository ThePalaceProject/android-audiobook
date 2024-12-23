dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":org.librarysimplified.audiobook.api"))
    implementation(project(":org.librarysimplified.audiobook.downloads"))
    implementation(project(":org.librarysimplified.audiobook.lcp.downloads"))
    implementation(project(":org.librarysimplified.audiobook.license_check.api"))
    implementation(project(":org.librarysimplified.audiobook.license_check.spi"))
    implementation(project(":org.librarysimplified.audiobook.manifest.api"))
    implementation(project(":org.librarysimplified.audiobook.manifest_fulfill.basic"))
    implementation(project(":org.librarysimplified.audiobook.manifest_fulfill.spi"))
    implementation(project(":org.librarysimplified.audiobook.manifest_parser.api"))
    implementation(project(":org.librarysimplified.audiobook.manifest_parser.extension_spi"))
    implementation(project(":org.librarysimplified.audiobook.parser.api"))
    implementation(project(":org.librarysimplified.audiobook.time_tracking"))

    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.constraintlayout.core)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.customview.poolingcontainer)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.views)
    implementation(libs.androidx.emoji2.views.helper)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.interpolator)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.livedata.core)
    implementation(libs.androidx.lifecycle.livedata.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.media)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.vectordrawable)
    implementation(libs.androidx.vectordrawable.animated)
    implementation(libs.androidx.viewpager)
    implementation(libs.androidx.viewpager2)
    implementation(libs.google.guava)
    implementation(libs.google.material)
    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.joda.time)
    implementation(libs.kabstand)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core.jvm)
    implementation(libs.media3.common)
    implementation(libs.media3.session)
    implementation(libs.palace.http.api)
    implementation(libs.palace.http.downloads)
    implementation(libs.palace.theme)
    implementation(libs.r2.lcp)
    implementation(libs.r2.shared)
    implementation(libs.rxandroid2)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)
}
