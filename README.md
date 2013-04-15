'If you want to track activity on your Android app, first clone this repository with

    git clone http://github.com/Parsely/parsely-android.git

This repository contains two directories:

* `HiParsely` is an Eclipse project demonstrating how to integrate the SDK into an app. It also contains the source code for the SDK itself, in the `com.parsely.parselyandroid` package.
* `Documentation` is the target directory for the Doxygen document generator


Integrating with Eclipse
------------------------

Adding Parsely to your Android app is easy!

1. Copy the `parsely` directory (under `src/com`) to your project's top-level
   package directory (in a default Eclipse project, this is `com`.) The
   directory tree should look like `src/com/parsely/parselyandroid`.
2. Copy the `jackson-core` and `jackson-mapper` JAR files into your project's
   `libs` directory.
3. In the Package Explorer, right click your project, select Build Path -> Add
   External Archives, select both of the Jackson JARs and click Open.
4. Add the following lines to your AndroidManifest.xml file:

    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

Including the SDK
-----------------

In any file that uses the Parsely SDK, be sure to add the line

    import com.parsely.parselyandroid.*;

at the top of the file.

Parsely Initialization
----------------------

Before using the SDK, you must initialize the Parsely object with your public
api key. This is usually best to do in the `MainActivity`'s `onCreate` method.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ParselyTracker.sharedInstance("examplesite.com", this);
    }

The `this` parameter is necessary to give Parsely access to the app's context.

Pageview Tracking
-----------------

To register a pageview event with Parsely, simply use the `track` call.

    ParselyTracker.sharedInstance().trackURL("http://examplesite.com/something-whatever.html");

This call requires the canonical URL of the page corresponding to the post currently being viewed.

You can also use

    ParselyTracker.sharedInstance().trackPostId("1987263412-12783461234");

which requires a string uniquely identifying the post to Parsely's systems.
