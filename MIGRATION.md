# Parse.ly Android SDK Migration Guide

This document acts as a guide for migration, detailing every breaking change that occurs between major versions of the Parse.ly Android SDK.

## Upgrading to version 4.x

When migrating to version 4.x of the Parse.ly Android SDK, you will encounter several changes that may require updates to your codebase. The following list details these breaking changes and recommended actions:

### Removed initialization `ParselyTracker#sharedInstace` methods

Previously, the SDK was initialized `ParselyTracker#sharedInstace` method, which should be replaced with `ParselyTracker#init` method:

```kotlin
ParselyTracker.sharedInstance("example.com", 30, this, true) // before

ParselyTracker.init("example.com", 30, this, true) // now
```

Without prior initialization, the SDK will now throw `ParselyNotInitializedException` when accessing `ParselyTracker#sharedInstance`.

*Action required*: Update how the Parsely SDK is initialized and accessed as presented above.

### Removed methods or access restriction
The following methods are no longer accessible from the `ParselyTracker` class:

- `engagementIsActive`
- `flushEventQueue`
- `flushTimerIsActive`
- `getDebug`
- `getEngagementInterval`
- `getFlushInterval`
- `getVideoEngagementInterval`
- `queueSize`
- `stopFlushTimer`
- `storedEventsCount`
- `videoIsActive`
  
Also, `toMap` methods are no longer available for API consumers:

- `ParselyMetadata#toMap`
- `ParselyVideoMetadata#toMap`

*Action required*: Those methods aren't needed to use the SDK. Remove any code segments that interact with them.

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
