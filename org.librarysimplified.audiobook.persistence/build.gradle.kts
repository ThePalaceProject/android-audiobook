dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":org.librarysimplified.audiobook.api"))
    implementation(project(":org.librarysimplified.audiobook.manifest.api"))

    implementation(libs.google.guava)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jcip.annotations)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.rxandroid2)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)

    // SQLite
    implementation(libs.io7m.anethum.api)
    implementation(libs.io7m.blackthorne.core)
    implementation(libs.io7m.blackthorne.jxe)
    implementation(libs.io7m.jaffirm.core)
    implementation(libs.io7m.jattribute.core)
    implementation(libs.io7m.jlexing.core)
    implementation(libs.io7m.junreachable)
    implementation(libs.io7m.jxe.core)
    implementation(libs.io7m.seltzer.api)
    implementation(libs.io7m.trasco.api)
    implementation(libs.io7m.trasco.vanilla)
    implementation(libs.io7m.trasco.xml.schemas)
    implementation(libs.xerces)
    implementation(libs.xerial.sqlite)
}
