audiobook-android
===

[![Build Status](https://img.shields.io/github/actions/workflow/status/ThePalaceProject/android-audiobook/android-main.yml)](https://github.com/ThePalaceProject/android-audiobook/actions?query=workflow%3A%22Android+CI+%28Authenticated%29%22)
[![Maven Central](https://img.shields.io/maven-central/v/org.thepalaceproject.audiobook/org.librarysimplified.audiobook.api.svg?style=flat-square)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.thepalaceproject.audiobook%22)
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
