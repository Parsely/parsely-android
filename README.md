Parsely Android SDK
===================

This library provides an interface to Parsely's pageview tracking system. It
provides similar functionality to the
[Parsely Javascript tracker](http://www.parsely.com/docs/integration/tracking/basic.html)
for Android apps. Full class-level documentation of this library can be found at the
[Parsely website](http://www.parsely.com/sdk/android/index.html).

Usage
-----

If you want to track activity on your Android app, first clone this repository with

    git clone http://github.com/Parsely/parsely-android.git

This repository's primary purpose is to host the open source Parse.ly Android SDK,
implemented as a Java module in `/parsely`. This module is symlinked in
the `/ParselyExample/app/src/main/java/com` directory as an example of
how to integrate the SDK in a typical Android Studio project. You can open
`ParselyExample` as an Android Studio project and explore a typical SDK integration.

Quickstart Guide
================

Integrating with Android Studio
-------------------------------

To integrate Parse.ly mobile tracking with your Android Studio app:

1. Copy the `parselyandroid` directory to your project's top-level package directory
    (in a default Android Studio project, this is
   `/app/src/main/java/com`). The directory tree should look like
   `/app/src/main/java/com/parsely/parselyandroid`.
2. In `Build -> Edit Libraries and Dependencies` under the `Dependencies` tab,
   use the green `+` to add two Library Dependencies:
   `org.codehaus.jackson:jackson-core-lgpl:1.9.13` and
   `org.codehaus.jackson:jackson-mapper-lgpl:1.9.13`
3. Add the following lines to your `AndroidManifest.xml` file:

        <uses-permission android:name="android.permission.INTERNET"/>
        <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>

4. Add the following lines to `app/build.gradle`:

        packagingOptions {
            exclude 'META-INF/LGPL2.1'
            exclude 'META-INF/LICENSE'
            exclude 'META-INF/NOTICE'
        }

Using the SDK
-------------

In any file that uses the Parsely SDK, be sure to add the line

    import com.parsely.parselyandroid.*;

at the top of the file.

Before using the toolkit, you must initialize the Parsely object with your public
api key. This is usually best to do in the `MainActivity`'s `onCreate` method.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ParselyTracker.sharedInstance("example.com", this);
    }

The `this` parameter is necessary to give Parsely access to the app's context.

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

To register a pageview event with Parsely, simply use the `track` call.

    ParselyTracker.sharedInstance().trackURL("http://example.com/something-whatever.html");

This call requires the canonical URL of the page corresponding to the post currently being viewed.

License
-------

    Copyright 2016 Parse.ly, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
