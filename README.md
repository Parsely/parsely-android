# Parsely Android SDK

The Parse.ly Android SDK is a Java library providing Parse.ly
tracking functionality to native Android apps. Like any other framework you might
include in your project, the Parse.ly SDK provides a programming
interface usable from your application code.

The [official repository](https://github.com/Parsely/parsely-android) is hosted on Github.

## Including the SDK in a project

If you want to track activity on your Android app, first
[download a release](https://github.com/Parsely/parsely-android/releases) or clone the
repository with:

    git clone http://github.com/Parsely/parsely-android.git

The repository's primary purpose is to host the open source Parse.ly Android SDK,
implemented as a Java module in `/parsely`. This module is symlinked in
the `/ParselyExample/app/src/main/java/com` directory as an example of
how to integrate the SDK in a typical Android Studio project. You can open
`ParselyExample` as an Android Studio project and explore a typical SDK integration.

To integrate Parse.ly mobile tracking with your Android Studio app:

1. Copy the `parselyandroid` directory to your project's top-level package directory
    (in a default Android Studio project, this is
   `/app/src/main/java/com`). The directory tree should look like
   `/app/src/main/java/com/parsely/parselyandroid`.
2. In `Build -\> Edit Libraries and Dependencies` under the `Dependencies` tab,
   use the green `+` to add two Library Dependencies:
   `org.codehaus.jackson:jackson-core-lgpl:1.9.13` and
   `org.codehaus.jackson:jackson-mapper-lgpl:1.9.13`
3. Add the following lines to your `AndroidManifest.xml` file:

        <uses-permission android:name="android.permission.INTERNET"/\>
        <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/\>

4. Add the following lines to `app/build.gradle`:

        packagingOptions {
            exclude 'META-INF/LGPL2.1'
            exclude 'META-INF/LICENSE'
            exclude 'META-INF/NOTICE'
        }


## Using the SDK

Full instructions and documentation can be found on
the [Parse.ly help page](https://www.parse.ly/help/integration/android-sdk/).
