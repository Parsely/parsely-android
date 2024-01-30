# Module parsely

The Parse.ly Android SDK is a library providing Parse.ly tracking functionality to native Android apps.

## Initialization

The SDK must be initialized before it can be used. 
It is recommended to initialize the SDK as early as possible in the app lifecycle. 
The SDK can be initialized with the following code:

```kotlin
ParselyTracker.init("example.com", /*site id*/ 30, /*flush interval*/ this, /*context*/ false, /*dry-run*/)
```

## Usage

The SDK can be used in two ways, depending on the needs of the consuming app

### Via a static call
To obtain a reference to the SDK statically, one can use `sharedInstance()` method:

```kotlin
ParselyTracker.sharedInstance().trackPageView("example.com")
```

### Via an interface

It's possible to use the SDK via an interface, which allows for easier testing and mocking of the SDK.

```kotlin
fun onCreate(...) {
    ParselyTracker.init(...)
    val tracker = ParselyTracker.sharedInstance()

    val someClass = SomeClass(tracker)
    someClass.openArticle()
}

class SomeClass(val tracker: ParselyTracker) {
    fun openArticle() {
        tracker.startEngagement()
    }
}
```
