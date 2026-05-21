---
name: parsely-android-testing
description: >
  Write unit tests for Parse.ly Android tracking using FakeParselyTracker. Provides a ready-to-use
  fake implementation of the ParselyTracker interface and test patterns for asserting on pageviews,
  engagement, video, and conversion events. Use this skill when the user wants to test Parse.ly
  tracking calls, mock ParselyTracker, write unit tests for a tracked screen, or verify that
  tracking events are sent correctly.
---

# Parse.ly Android SDK — Testing

## FakeParselyTracker

This skill bundles a ready-to-use fake at `references/FakeParselyTracker.kt`. Copy it into the
project's **`test` source set** (never `main`) and update the package declaration to match the project. It implements every method of the
`ParselyTracker` interface and records calls so tests can assert on them.

If the SDK adds new interface methods in a future release, the fake will fail to compile — update
it to match the new interface at that point.

## Test patterns

Create a new `FakeParselyTracker` instance per test. Inject it via the class constructor — this
is why all classes that track should receive `ParselyTracker` as a parameter rather than calling
`sharedInstance()` directly.

### Asserting pageview and engagement

```kotlin
@Test
fun `when screen becomes visible, then pageview and engagement are tracked`() {
    val fakeTracker = FakeParselyTracker()
    val viewModel = ArticleViewModel(parselyTracker = fakeTracker)

    viewModel.onScreenVisible("https://example.com/article")

    assertThat(fakeTracker.trackedPageviews).containsExactly("https://example.com/article")
    assertThat(fakeTracker.engagementStarts).containsExactly("https://example.com/article")
}

@Test
fun `when screen is hidden, then engagement is stopped`() {
    val fakeTracker = FakeParselyTracker()
    val viewModel = ArticleViewModel(parselyTracker = fakeTracker)
    viewModel.onScreenVisible("https://example.com/article")

    viewModel.onScreenHidden()

    assertThat(fakeTracker.engagementStopCount).isEqualTo(1)
}
```

### Asserting video tracking

```kotlin
@Test
fun `when video plays, then trackPlay is called with correct metadata`() {
    val fakeTracker = FakeParselyTracker()
    val viewModel = VideoViewModel(parselyTracker = fakeTracker)

    viewModel.onPlay("https://example.com/post", "video-123", 120)

    assertThat(fakeTracker.videoPlays).hasSize(1)
    assertThat(fakeTracker.videoPlays.first().url).isEqualTo("https://example.com/post")
}

@Test
fun `when video is paused, then trackPause is called`() {
    val fakeTracker = FakeParselyTracker()
    val viewModel = VideoViewModel(parselyTracker = fakeTracker)

    viewModel.onPause()

    assertThat(fakeTracker.videoPauseCount).isEqualTo(1)
}

@Test
fun `when video is stopped, then resetVideo is called`() {
    val fakeTracker = FakeParselyTracker()
    val viewModel = VideoViewModel(parselyTracker = fakeTracker)

    viewModel.onStop()

    assertThat(fakeTracker.videoResetCount).isEqualTo(1)
}
```

### Asserting conversions

```kotlin
@Test
fun `when subscription completes, then conversion is tracked with correct label`() {
    val fakeTracker = FakeParselyTracker()
    val viewModel = SubscriptionViewModel(parselyTracker = fakeTracker)

    viewModel.onSubscriptionCompleted("https://example.com/subscribe")

    assertThat(fakeTracker.conversions).containsExactly(
        FakeParselyTracker.Conversion(
            url = "https://example.com/subscribe",
            type = ConversionType.SUBSCRIPTION,
            label = "monthly_plan",
        )
    )
}
```

## Checklist

- [ ] `FakeParselyTracker` is in the `test` source set, not `main`
- [ ] Each test creates its own `FakeParselyTracker` instance — don't share state between tests
- [ ] At least one test per tracked screen verifies the pageview URL
- [ ] Screens with engagement tracking have a test that verifies `stopEngagement` is called
- [ ] Conversion tracking has a test that verifies both the `ConversionType` and `conversionLabel`
