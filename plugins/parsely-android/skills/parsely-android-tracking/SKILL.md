---
name: parsely-android-tracking
description: >
  Add Parse.ly tracking calls to Android screens and features: pageview, engagement time,
  video, conversion, and metadata. Use this skill when the user wants to track a screen, article,
  video, or conversion event with Parse.ly — even if they just say "track this screen with Parse.ly"
  or "add Parse.ly tracking here". Assumes ParselyTracker is already initialized and injectable
  (see parsely-android-setup). Pair with parsely-android-testing to write unit tests for the
  tracking calls.
---

# Parse.ly Android SDK — Tracking

Assumes `ParselyTracker` is already initialized in `Application.onCreate()` and available for
injection. If not, run `parsely-android-setup` first.

Pass `ParselyTracker` into whatever class owns the tracking logic — ViewModel, Activity, Fragment,
or any other class. The examples below use a ViewModel but the pattern applies to any class.

## Page view + engagement tracking

Call `trackPageview` once when a screen loads. Call `startEngagement` immediately after to begin
measuring time-on-content. Call `stopEngagement` when the user leaves — the right moment depends
on the app's navigation and UX model.

```kotlin
class ArticleViewModel(
    private val parselyTracker: ParselyTracker,
) : ViewModel() {

    fun onScreenVisible(articleUrl: String) {
        parselyTracker.trackPageview(url = articleUrl)
        parselyTracker.startEngagement(url = articleUrl)
    }

    fun onScreenHidden() {
        parselyTracker.stopEngagement()
    }
}
```

Wire `onScreenHidden()` wherever the screen stops being visible — Fragment/Activity `onPause`,
a Compose `DisposableEffect`, a player state callback, etc.

## Video tracking

`trackPause` and `resetVideo` behave differently — choose based on the user's intent:
- **`trackPause`**: user paused; calling `trackPlay` again for the same video won't re-send `videostart` — models "resume"
- **`resetVideo`**: user stopped; next `trackPlay` sends a fresh `videostart` — models "stop and restart"

```kotlin
class VideoViewModel(
    private val parselyTracker: ParselyTracker,
) : ViewModel() {

    fun onPlay(postUrl: String, videoId: String, durationSecs: Int) {
        parselyTracker.trackPlay(
            url = postUrl,
            videoMetadata = ParselyVideoMetadata(
                /* authors =   */ null,
                /* videoId =   */ videoId,
                /* section =   */ null,
                /* tags =      */ null,
                /* thumbUrl =  */ null,
                /* title =     */ "My Video Title",
                /* pubDate =   */ null,
                /* duration =  */ durationSecs
            )
        )
    }

    fun onPause() = parselyTracker.trackPause()

    fun onStop() = parselyTracker.resetVideo()
}
```

Call `onPause()` / `onStop()` at the right moment for the player — this might be a lifecycle event,
a player state callback, a PiP transition, or anything else that signals the user is no longer watching.

## Conversion tracking

```kotlin
class SubscriptionViewModel(
    private val parselyTracker: ParselyTracker,
) : ViewModel() {

    fun onSubscriptionCompleted(currentUrl: String) {
        parselyTracker.trackConversion(
            url = currentUrl,
            conversionType = ConversionType.SUBSCRIPTION,
            conversionLabel = "monthly_plan",
        )
    }
}
```

For available `ConversionType` values, inspect the enum using LSP or search the resolved Gradle dependency.

## Metadata (app-only content)

Only needed when the URL is not accessible over the internet (i.e. app-only content). Otherwise,
metadata will be gathered by Parse.ly's crawling infrastructure.

Add `urlMetadata` to the `trackPageview` call from wherever you already call it. Inspect the
`ParselyMetadata` constructor (via LSP or the resolved Gradle dependency) for all available fields.
The example below shows the most commonly used ones:

```kotlin
parselyTracker.trackPageview(
    url = "https://example.com/app-only/article-123",
    urlMetadata = ParselyMetadata(
        title = "Article Title",
        authors = listOf("Jane Doe"),
        pubDate = Calendar.getInstance().apply { set(2024, Calendar.JANUARY, 15) },
        // see ParselyMetadata for full list of available fields
    )
)
```

## Multi-site tracking

For projects that serve multiple Parse.ly sites, every tracking method accepts a `siteIdSource`
parameter — pass `SiteIdSource.Custom("other-site.com")` to override the default site ID for that event.

## Common mistakes to avoid

| Mistake | Why it's a problem |
|---------|-------------------|
| Forgetting `stopEngagement()` when leaving a screen | Background heartbeats inflate engaged-time metrics |
