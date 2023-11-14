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
    implementation(libs.androidx.arch.core.common)
    implementation(libs.androidx.arch.core.runtime)
    implementation(libs.androidx.asynclayoutinflater)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.constraintlayout.core)
    implementation(libs.androidx.constraintlayout.solver)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.runtime)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.cursoradapter)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.customview.poolingcontainer)
    implementation(libs.androidx.datastore.android)
    implementation(libs.androidx.datastore.core.android)
    implementation(libs.androidx.datastore.core.okio)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.views)
    implementation(libs.androidx.emoji2.views.helper)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.interpolator)
    implementation(libs.androidx.legacy.support.core.ui)
    implementation(libs.androidx.legacy.support.core.utils)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.livedata.core)
    implementation(libs.androidx.lifecycle.livedata.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.loader)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.media)
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.common.ktx)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.print)
    implementation(libs.androidx.recycler.view)
    implementation(libs.androidx.room.common)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.slidingpanelayout)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.tracing)
    implementation(libs.androidx.transition)
    implementation(libs.androidx.transition.ktx)
    implementation(libs.androidx.vectordrawable)
    implementation(libs.androidx.versionedparcelable)
    implementation(libs.androidx.viewbinding)
    implementation(libs.androidx.viewpager)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)
    implementation(libs.google.exoplayer)
    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.google.material)
    implementation(libs.irradia.fieldrush.api)
    implementation(libs.irradia.fieldrush.vanilla)
    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core.jvm)
    implementation(libs.logback.android)
    implementation(libs.okhttp3)
    implementation(libs.okio)
    implementation(libs.palace.http.api)
    implementation(libs.palace.http.vanilla)
    implementation(libs.palace.theme)
    implementation(libs.rxjava)
    implementation(libs.slf4j)

    if (project.hasProperty("org.thepalaceproject.audiobook.demo.with_findaway")) {
        if (project.property("org.thepalaceproject.audiobook.demo.with_findaway") == "true") {
            implementation(libs.dagger)
            implementation(libs.exoplayer2.core)
            implementation(libs.findaway)
            implementation(libs.findaway.common)
            implementation(libs.findaway.listening)
            implementation(libs.findaway.persistence)
            implementation(libs.findaway.play.android)
            implementation(libs.google.gson)
            implementation(libs.javax.inject)
            implementation(libs.koin.android)
            implementation(libs.koin.core)
            implementation(libs.koin.core.jvm)
            implementation(libs.moshi)
            implementation(libs.moshi.adapters)
            implementation(libs.moshi.kotlin)
            implementation(libs.okhttp3)
            implementation(libs.okhttp3.logging.interceptor)
            implementation(libs.palace.findaway)
            implementation(libs.retrofit2)
            implementation(libs.retrofit2.adapter.rxjava)
            implementation(libs.retrofit2.converter.gson)
            implementation(libs.retrofit2.converter.moshi)
            implementation(libs.rxandroid)
            implementation(libs.rxrelay)
            implementation(libs.sqlbrite)
            implementation(libs.stately.common)
            implementation(libs.stately.concurrency)
            implementation(libs.timber)
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
