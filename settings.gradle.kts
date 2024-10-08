import java.util.Properties

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

val lcpDRM =
    propertyBooleanOptional("org.thepalaceproject.lcp.enabled", false)
val findawayDRM =
    propertyBooleanOptional("org.thepalaceproject.findaway.enabled", false)
val overdriveDRM =
    propertyBooleanOptional("org.thepalaceproject.overdrive.enabled", false)

println("DRM: org.thepalaceproject.lcp.enabled       : $lcpDRM")
println("DRM: org.thepalaceproject.findaway.enabled  : $findawayDRM")
println("DRM: org.thepalaceproject.overdrive.enabled : $overdriveDRM")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("$rootDir/org.thepalaceproject.android.platform/build_libraries.toml"))
        }
    }

    /*
     * Conditionally enable LCP DRM.
     */

    val lcpDRMEnabled: Boolean =
        propertyBooleanOptional("org.thepalaceproject.lcp.enabled", false)

    val credentialsPath =
        propertyOptional("org.thepalaceproject.app.credentials.palace")

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

        /*
         * Findaway access.
         */

        if (findawayDRM) {
            maven {
                url = uri("http://maven.findawayworld.com/artifactory/libs-release/")
                isAllowInsecureProtocol = true
            }
        }

        /*
         * Enable access to various credentials-gated elements.
         */

        if (lcpDRMEnabled) {
            val filePath: String =
                when (val lcpProfile = property("org.thepalaceproject.lcp.profile")) {
                    "prod", "test" -> {
                        "${credentialsPath}/LCP/Android/build_lcp_${lcpProfile}.properties"
                    }
                    else -> {
                        throw GradleException("Unrecognized LCP profile: $lcpProfile")
                    }
                }

            val lcpProperties = Properties()
            lcpProperties.load(File(filePath).inputStream())

            ivy {
                name = "LCP"
                url = uri(lcpProperties.getProperty("org.thepalaceproject.lcp.repositoryURI"))
                patternLayout {
                    artifact(lcpProperties.getProperty("org.thepalaceproject.lcp.repositoryLayout"))
                }
                metadataSources {
                    artifact()
                }
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
include(":org.librarysimplified.audiobook.lcp.downloads")
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
include(":org.librarysimplified.audiobook.media3")
include(":org.librarysimplified.audiobook.mocking")
include(":org.librarysimplified.audiobook.parser.api")
include(":org.librarysimplified.audiobook.tests")
include(":org.librarysimplified.audiobook.time_tracking")
include(":org.librarysimplified.audiobook.views")

if (findawayDRM) {
    include(":org.librarysimplified.audiobook.audioengine")
}