android {
    packagingOptions {
        exclude("META-INF/LICENSE.md")
        exclude("META-INF/LICENSE-notice.md")
    }
}

val dependencyObjects = listOf(
    libs.androidx.activity,
    libs.androidx.activity.ktx,
    libs.androidx.annotation,
    libs.androidx.appcompat,
    libs.androidx.appcompat.resources,
    libs.androidx.cardview,
    libs.androidx.constraintlayout,
    libs.androidx.coordinatorlayout,
    libs.androidx.core,
    libs.androidx.core.ktx,
    libs.androidx.customview,
    libs.androidx.drawerlayout,
    libs.androidx.fragment,
    libs.androidx.fragment.ktx,
    libs.androidx.lifecycle.common,
    libs.androidx.lifecycle.extensions,
    libs.androidx.lifecycle.livedata,
    libs.androidx.lifecycle.livedata.core,
    libs.androidx.lifecycle.livedata.core.ktx,
    libs.androidx.lifecycle.livedata.ktx,
    libs.androidx.lifecycle.viewmodel,
    libs.androidx.lifecycle.viewmodel.savedstate,
    libs.androidx.preference,
    libs.androidx.preference.ktx,
    libs.androidx.recycler.view,
    libs.androidx.savedstate,
    libs.androidx.test.espresso,
    libs.androidx.test.ext.junit,
    libs.androidx.test.orchestrator,
    libs.androidx.test.rules,
    libs.androidx.test.runner,
    libs.google.failureaccess,
    libs.google.guava,
    libs.google.material,
    libs.irradia.mime.api,
    libs.irradia.mime.vanilla,
    libs.jackson.annotations,
    libs.jackson.core,
    libs.jackson.databind,
    libs.joda.time,
    libs.junit,
    libs.junit.jupiter.api,
    libs.junit.jupiter.engine,
    libs.kotlin.reflect,
    libs.kotlin.stdlib,
    libs.logback.android,
    libs.logback.classic,
    libs.mockito.core,
    libs.mockito.kotlin,
    libs.nypl.theme,
    libs.okhttp3,
    libs.palace.http.api,
    libs.palace.http.vanilla,
    libs.quicktheories,
    libs.r2.shared,
    libs.r2.streamer,
    libs.rxjava,
    libs.slf4j,

    project(":org.librarysimplified.audiobook.api"),
    project(":org.librarysimplified.audiobook.downloads"),
    project(":org.librarysimplified.audiobook.feedbooks"),
    project(":org.librarysimplified.audiobook.http"),
    project(":org.librarysimplified.audiobook.json_canon"),
    project(":org.librarysimplified.audiobook.json_web_token"),
    project(":org.librarysimplified.audiobook.lcp"),
    project(":org.librarysimplified.audiobook.lcp.license_status"),
    project(":org.librarysimplified.audiobook.license_check.api"),
    project(":org.librarysimplified.audiobook.license_check.spi"),
    project(":org.librarysimplified.audiobook.manifest.api"),
    project(":org.librarysimplified.audiobook.manifest_fulfill.api"),
    project(":org.librarysimplified.audiobook.manifest_fulfill.basic"),
    project(":org.librarysimplified.audiobook.manifest_fulfill.opa"),
    project(":org.librarysimplified.audiobook.manifest_fulfill.spi"),
    project(":org.librarysimplified.audiobook.manifest_parser.api"),
    project(":org.librarysimplified.audiobook.manifest_parser.extension_spi"),
    project(":org.librarysimplified.audiobook.manifest_parser.webpub"),
    project(":org.librarysimplified.audiobook.mocking"),
    project(":org.librarysimplified.audiobook.open_access"),
    project(":org.librarysimplified.audiobook.parser.api"),
    project(":org.librarysimplified.audiobook.rbdigital"),
    project(":org.librarysimplified.audiobook.views"),
)

dependencies {
    for (dep in dependencyObjects) {
        androidTestImplementation(dep)
    }

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.resources)
}

afterEvaluate {
    tasks.matching { task -> task.name.contains("UnitTest") }
        .forEach { task -> task.enabled = true }
}
