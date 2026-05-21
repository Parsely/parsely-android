---
name: parsely-android-setup
description: >
  Add the Parse.ly Android SDK to an existing project: Gradle dependency, Application-class
  initialization, and wiring ParselyTracker so it can be injected rather than fetched as a
  singleton. Use this skill when the user wants to integrate Parse.ly for the first time, add
  the SDK to a project, initialize ParselyTracker, or set up the Parse.ly dependency. Pair with
  parsely-android-tracking to add actual tracking calls, and parsely-android-testing for unit tests.
---

# Parse.ly Android SDK — Setup

## Step 1: Probe the project structure

Before writing any code, read:

1. The **app-module `build.gradle` or `build.gradle.kts`** — identify existing dependencies and what dependency declaration convention the project uses (direct, version catalog, `buildSrc`, etc.)
2. Any existing **`Application` subclass** — search for `class.*Application` in the main source set
3. **`AndroidManifest.xml`** — confirm whether an Application class is already registered, and check the package name

From this, determine:
- **Language**: Kotlin, Java, or mixed (generate code in whichever the target file uses; prefer Kotlin for new files in a mixed project)
- **DI setup**: how the project wires dependencies (DI framework, manual construction, service locator, etc.)
- **Application class**: exists or needs creating
- **`siteId`**: ask the user if not visible in existing code — it's the domain shown in Parse.ly dashboard under Settings → API (e.g. `"yoursite.com"`)

## Step 2: Add the Gradle dependency

The Maven coordinates are:

- **Group**: `com.parsely`
- **Artifact**: `parsely`
- **Repository**: Maven Central

Fetch the latest version by visiting https://api.github.com/repos/Parsely/parsely-android/releases/latest and reading the `tag_name` field from the response.

Strip any leading `v` from the tag to get the version string. Add the dependency using whatever convention the project already uses. Maven Central is required in the repository list — it is present in most projects by default.

## Step 3: Initialize in Application.onCreate()

`ParselyTracker.init()` must be called **exactly once**, **before** any tracking call, in `Application.onCreate()`. Never initialize in an Activity — it may be created after another component has already tried to track. Calling `init()` a second time throws `ParselyAlreadyInitializedException`, so guard against double-initialization if the app already has partial Parse.ly wiring.

Parameters:
| Parameter | Type | Notes |
|-----------|------|-------|
| `siteId` | `String` | Your Parse.ly site ID, e.g. `"example.com"` |
| `flushInterval` | `Int` | Seconds between event flushes; `60` is a sensible default. Lower values (e.g. `10`) give faster feedback during development |
| `context` | `Context` | Pass the Application `this` |
| `dryRun` | `Boolean` | `true` = events logged but **not** sent to servers. Use `BuildConfig.DEBUG` so production always sends |

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ParselyTracker.init(
            siteId = "YOUR_SITE_ID",
            flushInterval = 60, // consider 10 during development for faster feedback
            context = this,
            dryRun = BuildConfig.DEBUG,
        )
    }
}
```

If no `Application` subclass exists, create one and register it in `AndroidManifest.xml`:
```xml
<application android:name=".MyApplication" ...>
```

## Step 4: Wire up ParselyTracker for injection

What makes tracking code testable is that every class that tracks receives a `ParselyTracker` **interface** reference — never calls `sharedInstance()` itself.

Resolve `ParselyTracker.sharedInstance()` once at the app's entry point and wire it into the rest of the project using whatever mechanism the project already uses — DI framework binding, manual constructor injection, service locator, etc. The key rule: `sharedInstance()` belongs at the wiring layer, not inside any class that holds business logic.

## Common mistakes to avoid

| Mistake | Why it's a problem |
|---------|-------------------|
| Calling `init()` in an Activity | SDK may be uninitialized when another component fires first |
| Calling `sharedInstance()` inside feature code | Couples logic to singleton, blocks unit testing |
