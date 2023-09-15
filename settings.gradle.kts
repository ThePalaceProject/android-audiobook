pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

fun propertyOptional(name: String): String? {
    val map = settings.extra
    if (map.has(name)) {
        return map[name] as String?
    }
    return null
}

fun property(name: String): String {
    return propertyOptional(name) ?: throw GradleException("Required property $name is not defined.")
}

fun propertyBooleanOptional(name: String, defaultValue: Boolean): Boolean {
    val value = propertyOptional(name) ?: return defaultValue
    return value.toBooleanStrict()
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("$rootDir/org.thepalaceproject.android.platform/build_libraries.toml"))
        }
    }

    /*
     * The set of repositories used to resolve library dependencies. The order is significant!
     */

    repositories {
        mavenLocal()
        mavenCentral()
        google()

        /*
         * Allow access to the Sonatype snapshots repository.
         */

        maven {
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        }

        /*
         * Allow access to Jitpack. This is used by, for example, Readium.
         */

        maven {
            url = uri("https://jitpack.io")
        }

        if (propertyBooleanOptional("org.thepalaceproject.audiobook.demo.with_findaway", false)) {
            maven {
                url = uri("http://maven.findawayworld.com/artifactory/libs-release/")
                isAllowInsecureProtocol = true
            }
        }

        /*
         * Allow access to jcenter. This is needed for Exoplayer. This badly needs to be
         * removed/upgraded.
         */

        jcenter()
    }
}

rootProject.name = "org.librarysimplified.audiobook"

include(":org.librarysimplified.audiobook.api")
include(":org.librarysimplified.audiobook.demo")
include(":org.librarysimplified.audiobook.downloads")
include(":org.librarysimplified.audiobook.feedbooks")
include(":org.librarysimplified.audiobook.http")
include(":org.librarysimplified.audiobook.json_canon")
include(":org.librarysimplified.audiobook.json_web_token")
include(":org.librarysimplified.audiobook.lcp")
include(":org.librarysimplified.audiobook.lcp.license_status")
include(":org.librarysimplified.audiobook.license_check.api")
include(":org.librarysimplified.audiobook.license_check.spi")
include(":org.librarysimplified.audiobook.manifest.api")
include(":org.librarysimplified.audiobook.manifest_fulfill.api")
include(":org.librarysimplified.audiobook.manifest_fulfill.basic")
include(":org.librarysimplified.audiobook.manifest_fulfill.opa")
include(":org.librarysimplified.audiobook.manifest_fulfill.spi")
include(":org.librarysimplified.audiobook.manifest_parser.api")
include(":org.librarysimplified.audiobook.manifest_parser.extension_spi")
include(":org.librarysimplified.audiobook.manifest_parser.webpub")
include(":org.librarysimplified.audiobook.mocking")
include(":org.librarysimplified.audiobook.open_access")
include(":org.librarysimplified.audiobook.parser.api")
include(":org.librarysimplified.audiobook.rbdigital")
include(":org.librarysimplified.audiobook.views")
include(":org.librarysimplified.audiobook.tests")
include(":org.librarysimplified.audiobook.tests.device")
