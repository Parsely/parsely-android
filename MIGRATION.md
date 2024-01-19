# Parse.ly Android SDK Migration Guide

This document acts as a guide for migration, detailing every breaking change that occurs between major versions of the Parse.ly Android SDK.

## Upgrading to version 4.x

When migrating to version 4.x of the Parse.ly Android SDK, you will encounter several changes that may require updates to your codebase. The following list details these breaking changes and recommended actions:

### Removed `ParselyTracker#sharedInstace` methods

Previously, the SDK was initialized `ParselyTracker#sharedInstace` method, which should be replaced with `ParselyTracker#init` method:

```kotlin
ParselyTracker.sharedInstance("example.com", 30, this, true) // before

ParselyTracker.init("example.com", 30, this, true) // now
```

The `ParselyTracker` was later accessed via a second variant of `ParselyTracker#sharedInstance`. All SDK methods are now available statically from `ParselyTracker`:

```kotlin
ParselyTracker.sharedInstance().trackPageview("http://example.com/article1.html", "http://example.com/", null, null) // before

ParselyTracker.trackPageview("http://example.com/article1.html", "http://example.com/", null, null) // now
```

*Action required*: Update how the Parsely SDK is initialized and accessed as presented above.

### Removed methods or access restriction
The following methods have been removed from the `ParselyTracker` class:

- `flushEventQueue`
- `getDebug`
- `queueSize`
- `storedEventsCount`
- `stopFlushTimer`

The `toMap` methods that convert metadata objects to maps are no longer available for API consumers:

- `ParselyMetadata#toMap`
- `ParselyVideoMetadata#toMap`

*Action required*: Those methods shouldn't be needed to use the SDK.

### Restricted access to internal API connection
`ParselyAPIConnection`, which might have been previously used for direct API interactions, is no longer accessible to the API consumer.

*Action required*: Remove any code segments that interact with `ParselyAPIConnection` as this class is now an implementation detail of the SDK.

### Non-nullable `urlRef` parameters
Parameters that were previously nullable when calling various tracking methods are now non-nullable.

- In the `ParselyTracker` class, the `urlRef` parameter in the following methods requires a string value:
  - `trackPageview`
  - `startEngagement`
  - `trackPlay`

*Action required*: When calling these methods, ensure you provide a valid string. If there is no value available, pass an empty string `""` instead of `null`.

### Debug configuration change
Configuring the debug mode during the SDK initialization has been simplified to a "dry run" mode flag.

- The method `ParselyTracker#setDebug` is no longer available.

*Action required*: When initializing the `ParselyTracker`, set the `dryRun` property to `true`.

For clarification on any of these changes or assistance with migrating your code to the latest version of the Parse.ly Android SDK, please contact our support team.
