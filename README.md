Parsely Android SDK
===================

This library provides an interface to Parsely's pageview tracking system. It
provides similar functionality to the [Parsely Javascript tracker](http://www.parsely.com/docs/integration/tracking/basic.html)
for Android apps.

Documentation
-------------

Full class-level documentation of this library can be found at the
[Parsely website](http://www.parsely.com/sdk/android/index.html). This documentation
is generated from the code itself using [Doxygen](http://www.stack.nl/~dimitri/doxygen/).

Usage
-----

If you want to track activity on your Android app, first clone this repository with

    git clone http://github.com/Parsely/parsely-android.git

This repository contains three main directories:

* `HiParsely` is an Eclipse project demonstrating how to integrate the SDK
into an app. It also contains the source code of the Parsely toolkit, under
`src/com/parsely`
* `Documentation` is the target directory for the Doxygen document generator

Quickstart Guide
================

Integrating with Eclipse
------------------------

Adding Parsely to your Android app is easy!

1. Copy the `parsely` directory (under `HiParsely/src/com`) to your project's top-level
   package directory (in a default Eclipse project, this is `com`.) The
   directory tree should look like `src/com/parsely/parselyandroid`.
2. Copy the `jackson-core` and `jackson-mapper` JAR files into your project's
   `libs` directory.
3. In the Package Explorer, right click your project, select Build Path -> Add
   External Archives, select both of the Jackson JARs and click Open.
4. Add the following lines to your AndroidManifest.xml file:

    `<uses-permission android:name="android.permission.INTERNET"/>`

    `<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>`

    `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>`

Including the SDK
-----------------

In any file that uses the Parsely SDK, be sure to add the line

    import com.parsely.parselyandroid.*;

at the top of the file.

Parsely Initialization
----------------------

Before using the toolkit, you must initialize the Parsely object with your public
api key. This is usually best to do in the `MainActivity`'s `onCreate` method.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ParselyTracker.sharedInstance("examplesite.com", this);
    }

The `this` parameter is necessary to give Parsely access to the app's context.

Flushing the Event Queue
------------------------

The Parsely toolkit maintains a queue of pageview events and periodically flushes it to the servers.
This helps extend the battery life of your users' Android devices. As a result of
this design, there may be some pageview events remaining in the queue at the time the
user exits your app. To make sure all of the queued events are flushed to the server
at this time, make sure to include a call to `flush()` in your main activity's
`onDestroy()` method:

    @Override
    protected void onDestroy() {
        ParselyTracker.sharedInstance().flush();
        super.onDestroy();
    }

Pageview Tracking
-----------------

To register a pageview event with Parsely, simply use the `track` call.

    ParselyTracker.sharedInstance().trackURL("http://examplesite.com/something-whatever.html");

This call requires the canonical URL of the page corresponding to the post currently being viewed.
