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

## Required: configure your site IDs

The SDK does not hardcode a collection endpoint. Parse.ly decides which endpoint your site's
data goes to, and the SDK learns it at build time.

**1. Apply the plugin** in your app module's `build.gradle`:

```groovy
plugins {
    id 'com.parsely.hosts'
}
```

**2. Declare every site ID your app tracks** in `parsely-apikeys.json`, next to that build file:

```json
{ "apikeys": ["example.com", "example.co.uk"] }
```

This must be exhaustive. It needs the site ID you pass to `ParselyTracker.init` *and* every
site ID you ever pass as `SiteIdSource.Custom`.

**3. Commit `parsely-apikeys.json`.** The generated `parsely-hosts.json` asset is a build
output written under `build/generated/` and does not belong in version control.

Your build fails if a declared site ID is not one Parse.ly recognises. If Parse.ly is
temporarily unreachable but the generated asset already covers every declared site ID, the
build warns and continues rather than failing on an outage.

### If you skip the plugin

The plugin is how a misconfiguration is caught. Without it nothing fails at build time and
**the SDK sends no analytics at all** — an app with no `parsely-hosts.json` asset logs an
error and drops every event. Events for a site ID missing from the asset are likewise dropped
rather than sent to the wrong endpoint.

## Using the SDK

Full instructions and documentation can be found on
the [Parse.ly help page](https://docs.parse.ly/android-sdk/).

## Migration to 4.0.0

Version 4.0.0 of the SDK introduces significant updates and breaking changes that enhance performance and add new features.
These changes may require modifications to your existing code. For detailed instructions on how to adapt your code to these changes, please refer to our [Migration Guide](MIGRATION.md).
