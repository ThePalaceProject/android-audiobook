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
     * Conditionally enable access to S3.
     */

    val s3RepositoryEnabled: Boolean =
        propertyBooleanOptional("org.thepalaceproject.s3.depend", false)
    val s3RepositoryAccessKey: String? =
        propertyOptional("org.thepalaceproject.aws.access_key_id")
    val s3RepositorySecretKey: String? =
        propertyOptional("org.thepalaceproject.aws.secret_access_key")

    if (s3RepositoryEnabled) {
        if (s3RepositoryAccessKey == null) {
            throw GradleException(
                "If the org.thepalaceproject.s3.depend property is set to true, " +
                    "the org.thepalaceproject.aws.access_key_id property must be defined."
            )
        }
        if (s3RepositorySecretKey == null) {
            throw GradleException(
                "If the org.thepalaceproject.s3.depend property is set to true, " +
                    "the org.thepalaceproject.aws.secret_access_key property must be defined."
            )
        }
    }

    /*
     * Conditionally enable LCP DRM.
     */

    val lcpDRMEnabled: Boolean =
        propertyBooleanOptional("org.thepalaceproject.lcp.enabled", false)

    if (lcpDRMEnabled && !s3RepositoryEnabled) {
        throw GradleException(
            "If the org.thepalaceproject.lcp.enabled property is set to true, " +
                "the org.thepalaceproject.s3.depend property must also be set to true."
        )
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
         * Optionally enable access to the S3 repository.
         */

        if (s3RepositoryEnabled) {
            maven {
                name = "S3 Snapshots"
                url = uri("s3://se-maven-repo/snapshots/")
                credentials(AwsCredentials::class) {
                    accessKey = s3RepositoryAccessKey
                    secretKey = s3RepositorySecretKey
                }
                mavenContent {
                    snapshotsOnly()
                }
            }

            maven {
                name = "S3 Releases"
                url = uri("s3://se-maven-repo/releases/")
                credentials(AwsCredentials::class) {
                    accessKey = s3RepositoryAccessKey
                    secretKey = s3RepositorySecretKey
                }
                mavenContent {
                    releasesOnly()
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
include(":org.librarysimplified.audiobook.persistence")
include(":org.librarysimplified.audiobook.tests")
include(":org.librarysimplified.audiobook.time_tracking")
include(":org.librarysimplified.audiobook.views")

if (findawayDRM) {
    include(":org.librarysimplified.audiobook.audioengine")
}