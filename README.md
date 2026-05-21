# Parsely Android SDK

[![Maven Central](https://img.shields.io/maven-central/v/com.parsely/parsely.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.parsely/parsely) [![Assemble project](https://github.com/Parsely/parsely-android/actions/workflows/readme.yml/badge.svg)](https://github.com/Parsely/parsely-android/actions/workflows/readme.yml) [![codecov](https://codecov.io/gh/Parsely/parsely-android/graph/badge.svg?token=M7PNYbYvKP)](https://codecov.io/gh/Parsely/parsely-android)

The Parse.ly Android SDK is a Java library providing Parse.ly tracking functionality to native
Android apps. Like any other framework you might include in your project, the Parse.ly SDK provides
a programming interface usable from your application code.

## Including the SDK in a project

The SDK is hosted on MavenCentral repository.

```groovy
implementation("com.parsely:parsely:<release_version>")
```

## Using the SDK

Full instructions and documentation can be found on
the [Parse.ly help page](https://docs.parse.ly/android-sdk/).

## AI-Assisted Development

A [Claude Code](https://claude.ai/code) plugin is available to help integrate and use this SDK:

```sh
claude plugin marketplace add Parsely/parsely-android
claude plugin install parsely-android@parsely-android-marketplace
```

The plugin provides three skills:

| Skill | Description |
|-------|-------------|
| `parsely-android-setup` | Add the SDK to a project: Gradle dependency, Application init, DI wiring |
| `parsely-android-tracking` | Add tracking calls: pageview, engagement, video, conversion, metadata |
| `parsely-android-testing` | Write unit tests using `FakeParselyTracker` |

## Migration to 4.0.0

Version 4.0.0 of the SDK introduces significant updates and breaking changes that enhance performance and add new features.
These changes may require modifications to your existing code. For detailed instructions on how to adapt your code to these changes, please refer to our [Migration Guide](MIGRATION.md).
