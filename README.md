audiobook-android
===

[![Build Status](https://img.shields.io/github/actions/workflow/status/ThePalaceProject/android-audiobook/android-main.yml)](https://github.com/ThePalaceProject/android-audiobook/actions?query=workflow%3A%22Android+CI+%28Authenticated%29%22)
[![Maven Central](https://img.shields.io/maven-central/v/org.thepalaceproject.audiobook/org.librarysimplified.audiobook.api.svg?style=flat-square)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.librarysimplified.audiobook%22)
[![Maven Central (snapshot)](https://img.shields.io/nexus/s/https/s01.oss.sonatype.org/org.thepalaceproject.audiobook/org.librarysimplified.audiobook.api.svg?style=flat-square)](https://s01.oss.sonatype.org/content/repositories/snapshots/org.thepalaceproject.audiobook/)

### Compilation

Make sure you clone this repository with `git clone --recursive`. 
If you forgot to use `--recursive`, then execute:

```
$ git submodule init
$ git submodule update --remote --recursive
```

```
$ ./gradlew clean assembleDebug test publish
```

### Project Structure

The project is divided into separate modules. Programmers wishing to use the API will primarily be
concerned with the [Core API](https://github.com/ThePalaceProject/android-audiobook/tree/develop/org.librarysimplified.audiobook.api),
but will also need to add [providers](#providers) to the classpaths of their projects in order
to actually do useful work. The API is designed to make it easy to develop an event-driven user
interface, but this project also includes a ready-made [player UI](#player_ui) that can be embedded
into applications. Additionally, audio engine providers that do not, by themselves, handle downloads
require callers to provide a _download provider_. Normally, this code would be provided directly
by applications (as applications tend to have centralized code to handle downloads), but a
[simple implementation](https://github.com/ThePalaceProject/android-audiobook/tree/develop/org.librarysimplified.audiobook.downloads)
is available to ease integration.

|Module|Description|
|------|-----------|
|[org.librarysimplified.audiobook.api](org.librarysimplified.audiobook.api)|AudioBook API (API specification)|
|[org.librarysimplified.audiobook.downloads](org.librarysimplified.audiobook.downloads)|AudioBook API (Download provider)|
|[org.librarysimplified.audiobook.feedbooks](org.librarysimplified.audiobook.feedbooks)|AudioBook API (Feedbooks-specific functionality)|
|[org.librarysimplified.audiobook.json_canon](org.librarysimplified.audiobook.json_canon)|AudioBook API (JSON canonicalization functionality)|
|[org.librarysimplified.audiobook.json_web_token](org.librarysimplified.audiobook.json_web_token)|AudioBook API (JSON web token functionality)|
|[org.librarysimplified.audiobook.lcp.license_status](org.librarysimplified.audiobook.lcp.license_status)|AudioBook API (LCP License Status document support)|
|[org.librarysimplified.audiobook.license_check.api](org.librarysimplified.audiobook.license_check.api)|AudioBook API (License check API)|
|[org.librarysimplified.audiobook.license_check.spi](org.librarysimplified.audiobook.license_check.spi)|AudioBook API (License check SPI)|
|[org.librarysimplified.audiobook.manifest.api](org.librarysimplified.audiobook.manifest.api)|AudioBook API (Manifest types)|
|[org.librarysimplified.audiobook.manifest_parser.api](org.librarysimplified.audiobook.manifest_parser.api)|AudioBook API (Manifest parser API)|
|[org.librarysimplified.audiobook.manifest_parser.extension_spi](org.librarysimplified.audiobook.manifest_parser.extension_spi)|AudioBook API (Manifest parser extension SPI)|
|[org.librarysimplified.audiobook.manifest_parser.webpub](org.librarysimplified.audiobook.manifest_parser.webpub)|AudioBook API (Readium WebPub manifest parser)|
|[org.librarysimplified.audiobook.mocking](org.librarysimplified.audiobook.mocking)|AudioBook API (Mock API implementation)|
|[org.librarysimplified.audiobook.open_access](org.librarysimplified.audiobook.open_access)|AudioBook API (Open access player implementation)|
|[org.librarysimplified.audiobook.parser.api](org.librarysimplified.audiobook.parser.api)|AudioBook API (Parser API)|
|[org.librarysimplified.audiobook.rbdigital](org.librarysimplified.audiobook.rbdigital)|AudioBook API (RBDigital-specific functionality)|
|[org.librarysimplified.audiobook.tests](org.librarysimplified.audiobook.tests)|AudioBook API (Test suite)|
|[org.librarysimplified.audiobook.tests.device](org.librarysimplified.audiobook.tests.device)|AudioBook API (On-device test suite)|
|[org.librarysimplified.audiobook.tests.sandbox](org.librarysimplified.audiobook.tests.sandbox)|AudioBook API (Sandbox)|
|[org.librarysimplified.audiobook.views](org.librarysimplified.audiobook.views)|AudioBook API (Standard UI components)|

### Changelog

The project currently uses [com.io7m.changelog](https://www.io7m.com/software/changelog/)
to manage release changelogs.

### Usage

1. Download (or synthesize) an [audio book manifest](#manifest_parsers). [Hadrien Gardeur](https://github.com/HadrienGardeur/audiobook-manifest/) publishes many example manifests in formats supported by the API.
2. Ask the API to [parse the manifest](#using_manifest_parsers).
3. (Optional) Ask the API to [perform license checks](#license_checking).
4. (Optional) Configure any [player extensions](#player_extensions) you may want to use.
5. Ask the API to [create an audio engine](#using_audio_engines) from the parsed manifest.
6. Make calls to the resulting [audio book](https://github.com/ThePalaceProject/android-audiobook/blob/develop/org.librarysimplified.audiobook.api/src/main/java/org/librarysimplified/audiobook/api/PlayerAudioBookType.kt) to download and play individual parts of the book.

See the provided [example project](https://github.com/NYPL-Simplified/audiobook-demo-android) for a
complete example that is capable of downloading and playing audio books.

### Dependencies

At a minimum, applications will need the Core API, one or more [manifest parser](#manifest_parsers)
implementations, and one or more [audio engine](#audio_engines) implementations. Use the following
Gradle dependencies to get a manifest parser that can parse the Readium WebPub manifest format, and 
an audio engine that can play non-encrypted audio books:

```
ext {
  nypl_audiobook_api_version = "4.0.0-SNAPSHOT"
}

dependencies {
  implementation "org.librarysimplified.audiobook:org.librarysimplified.audiobook.manifest_parser.webpub:${nypl_audiobook_api_version}"
  implementation "org.librarysimplified.audiobook:org.librarysimplified.audiobook.api:${nypl_audiobook_api_version}"
  implementation "org.librarysimplified.audiobook:org.librarysimplified.audiobook.open_access:${nypl_audiobook_api_version}"
}
```

### Versioning

The API is expected to follow [semantic versioning](https://semver.org/).

### Providers

The API uses a _service provider_ model in order to provide strong _modularity_ and to decouple
consumers of the API from specific implementations of the API. To this end, the API uses
[ServiceLoader](https://docs.oracle.com/javase/10/docs/api/java/util/ServiceLoader.html)
internally in order to allow new implementations of both [manifest parsers](#manifest_parsers) and
[audio engines](#audio_engines) to be registered and made available to client applications without
requiring any changes to the application code.

### Manifest Parsers <a id="manifest_parsers"/>

#### Overview

An audio book is typically delivered to the client via a _manifest_. A manifest is normally a
JSON description of the audio book that includes links to audio files, and other metadata. It is the
responsibility of a _manifest parser_ to turn a JSON AST into a typed manifest data structure
defined in the Core API.

#### Using Manifest Parsers <a id="using_manifest_parsers"/>

Programmers should make calls to the [ManifestParsers](https://github.com/ThePalaceProject/android-audiobook/blob/develop/org.librarysimplified.audiobook.manifest_parser.api/src/main/java/org/librarysimplified/audiobook/manifest_parser/api/ManifestParsers.kt)
class, passing in a byte array representing (typically) the raw text of a JSON manifest. The methods return a
`PlayerResult` value providing either the parsed manifest or a list of errors indicating why parsing
failed. The `ManifestParsers` class asks each registered [manifest parser](#creating_manifest_parsers)
whether or not it can parse the given raw data and picks the first one that claims that it can.
Programmers are not intended to have to use instances of the [PlayerManifestParserType](https://github.com/ThePalaceProject/android-audiobook/blob/develop/org.librarysimplified.audiobook.manifest_parser.api/src/main/java/org/librarysimplified/audiobook/manifest_parser/api/ManifestParserType.kt)
directly.

#### Creating Manifest Parsers <a id="creating_manifest_parsers"/>

Programmers will generally not need to create new manifest parsers, but will instead use one or
more of the [provided implementations](https://github.com/ThePalaceProject/android-audiobook/tree/develop/org.librarysimplified.audiobook.manifest_parser.webpub).
However, applications needing to use a new and unsupported manifest format will need to
provide and register new manifest parser implementations.

In order to add a new manifest parser, it's necessary to define a new class that implements
the [PlayerManifestParserType](https://github.com/ThePalaceProject/android-audiobook/blob/develop/org.librarysimplified.audiobook.manifest_parser.api/src/main/java/org/librarysimplified/audiobook/manifest_parser/api/ManifestParserProviderType.kt)
and defines a public, no-argument constructor. It's then necessary to register this class so that
`ServiceLoader` can find it by creating a resource file at
`META-INF/services/org.librarysimplified.audiobook.manifest_parser.api.ManifestParserProviderType` containing the fully
qualified name of the new class. The standard [WebPubParserProvider](https://github.com/ThePalaceProject/android-audiobook/blob/develop/org.librarysimplified.audiobook.manifest_parser.webpub/src/main/java/org/librarysimplified/audiobook/manifest_parser/webpub/WebPubParserProvider.kt)
class and its associated [service file](https://github.com/ThePalaceProject/android-audiobook/blob/develop/org.librarysimplified.audiobook.manifest_parser.webpub/src/main/resources/META-INF/services/org.librarysimplified.audiobook.manifest_parser.api.ManifestParserProviderType)
serve as minimal examples for new parser implementations. When a `jar` (or `aar`) file is placed on
the classpath containing both the class and the service file, `ServiceLoader` will find the
implementation automatically when the user asks for parser implementations.

Parsers are responsible for examining the given JSON AST and telling the caller whether or not they
think that they are capable of parsing the AST into a useful structure. For example,
[audio engine providers](#audio_engines) that require DRM might check the AST to see if the
required DRM metadata structures are present. The Core API will ask each parser implementation in
turn if the implementation can parse the given JSON, and the first implementation to respond in the
affirmative will be used. Implementations should take care to be honest; an implementation that
always claimed to be able to parse the given JSON would prevent other (possibly more suitable)
implementations from being considered.

### License Checking <a id="license_checking"/>

The API allows for opt-in _license checking_. Once a manifest has been
parsed, programmers can execute license checks on the manifest to verify
if the listening party actually has permission to hear the given audio
book.

Individual license checks are provided as implementations of the
[SingleLicenseCheckProviderType](https://github.com/ThePalaceProject/android-audiobook/blob/develop/org.librarysimplified.audiobook.license_check.spi/src/main/java/org/librarysimplified/audiobook/license_check/spi/SingleLicenseCheckProviderType.kt)
type. Programmers should pass in a list of desired single license check providers
to the [LicenseChecks](https://github.com/ThePalaceProject/android-audiobook/blob/develop/org.librarysimplified.audiobook.license_check.api/src/main/java/org/librarysimplified/audiobook/license_check/api/LicenseChecks.kt)
API for execution. The `LicenseChecks` API returns a list of the results
of license checks, and provides a simple `true/false` value indicating
whether or not playing should be permitted.

### Audio Engines <a id="audio_engines"/>

#### Overview

An _audio engine_ is a component that actually downloads and plays a given audio book.

#### Using Audio Engines <a id="using_audio_engines"/>

Given a parsed [manifest](#using_manifest_parsers), programmers should make calls to the methods
defined on the [PlayerAudioEngines](https://github.com/ThePalaceProject/android-audiobook/blob/develop/org.librarysimplified.audiobook.api/src/main/java/org/librarysimplified/audiobook/api/PlayerAudioEngines.kt)
class. Similarly to the `PlayerManifests` class, the `PlayerAudioEngines` class will ask each
registered [audio engine implementation](#creating_audio_engines) in turn if it is capable of
supporting the book described by the given manifest. Please consult the documentation for that
class for information on how to filter and/or prefer particular implementations. The
(somewhat arbitrary) default behaviour is to select all implementations that claim to be able to
support the given book, and then select the implementation that advertises the highest version number.

#### Creating Audio Engines <a id="creating_audio_engines"/>

Implementations must implement the [PlayerAudioEngineProviderType](https://github.com/ThePalaceProject/android-audiobook/blob/develop/org.librarysimplified.audiobook.api/src/main/java/org/librarysimplified/audiobook/api/PlayerAudioEngineProviderType.kt)
interface and register themselves in the same manner as [manifest parsers](#creating_manifest_parsers).

Creating a new audio engine provider is a fairly involved process. The provided
[ExoPlayer-based implementation](https://github.com/ThePalaceProject/android-audiobook/blob/develop/org.librarysimplified.audiobook.open_access/src/main/java/org/librarysimplified/audiobook/open_access/ExoEngineProvider.kt)
may serve as an example for new implementations.

In order to reduce duplication of code between audio engines, the downloading of books is
abstracted out into a [PlayerDownloadProviderType](https://github.com/ThePalaceProject/android-audiobook/blob/develop/org.librarysimplified.audiobook.api/src/main/java/org/librarysimplified/audiobook/api/PlayerDownloadProviderType.kt)
interface that audio engine implementations can call in order to perform the work of actually
downloading books. Implementations of this interface are actually provided by the calling programmer
as this kind of code is generally provided by the application using the audio engine.

#### Player Extensions <a id="player_extensions"/>

Audio engines may support [extensions](https://github.com/ThePalaceProject/android-audiobook/blob/develop/org.librarysimplified.audiobook.api/src/main/java/org/librarysimplified/audiobook/api/extensions/PlayerExtensionType.kt)
that allow for augmenting the behaviour of existing implementations. This
is primarily useful for, for example, adding unusual authentication
mechanisms that may be required by book distributors when downloading
book chapters. A list of extensions may be passed in to the `create`
method of the [PlayerAudioBookProviderType](https://github.com/ThePalaceProject/android-audiobook/blob/develop/org.librarysimplified.audiobook.api/src/main/java/org/librarysimplified/audiobook/api/PlayerAudioBookProviderType.kt)
interface. Extensions _must_ be explicitly passed in in order to be
used; passing in an empty list results in no extensions being used.
