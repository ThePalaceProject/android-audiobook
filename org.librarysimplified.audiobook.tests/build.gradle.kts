val dependencyObjects = listOf(
    libs.androidx.activity,
    libs.androidx.activity.ktx,
    libs.androidx.annotation,
    libs.androidx.appcompat,
    libs.androidx.cardview,
    libs.androidx.constraintlayout,
    libs.androidx.coordinatorlayout,
    libs.androidx.core,
    libs.androidx.core.ktx,
    libs.androidx.core.splashscreen,
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
    libs.bouncycastle,
    libs.bouncycastle.bcprov,
    libs.bouncycastle.pki,
    libs.bouncycastle.tls,
    libs.bytebuddy,
    libs.bytebuddy.agent,
    libs.google.failureaccess,
    libs.google.guava,
    libs.google.material,
    libs.irradia.fieldrush.api,
    libs.irradia.fieldrush.vanilla,
    libs.irradia.mime.api,
    libs.irradia.mime.vanilla,
    libs.jackson.annotations,
    libs.jackson.core,
    libs.jackson.databind,
    libs.joda.time,
    libs.junit,
    libs.junit.jupiter.api,
    libs.junit.jupiter.engine,
    libs.junit.jupiter.vintage,
    libs.junit.platform.commons,
    libs.junit.platform.engine,
    libs.kotlin.reflect,
    libs.kotlin.stdlib,
    libs.logback.classic,
    libs.logback.core,
    libs.mockito.core,
    libs.mockito.kotlin,
    libs.net.minidev.accessors.smart,
    libs.net.minidev.json.smart,
    libs.nimbus.jose.jwt,
    libs.objenesis,
    libs.okhttp3,
    libs.okhttp3.mockwebserver,
    libs.okio,
    libs.opentest,
    libs.ow2,
    libs.ow2.asm,
    libs.ow2.asm.commons,
    libs.ow2.asm.tree,
    libs.palace.http.api,
    libs.palace.http.vanilla,
    libs.palace.theme,
    libs.quicktheories,
    libs.r2.shared,
    libs.r2.streamer,
    libs.reactive.streams,
    libs.rxandroid2,
    libs.rxjava,
    libs.rxjava2,
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
        implementation(dep)
        testImplementation(dep)
    }
}

afterEvaluate {
    tasks.matching { task -> task.name.contains("UnitTest") }
        .forEach { task -> task.enabled = true }
}
