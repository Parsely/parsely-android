package com.parsely.parselyandroid

import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ParselyTrackerTest {

    @Test(expected = ParselyNotInitializedException::class)
    fun `given no prior initialization, when executing a method, throw the exception`() {
        ParselyTracker.sharedInstance().startEngagement("url")
    }

    @Test(expected = ParselyAlreadyInitializedException::class)
    fun `given prior initialization, when initializing, throw an exception`() {
        ParselyTracker.init(siteId = "", context = RuntimeEnvironment.getApplication())

        ParselyTracker.init(siteId = "", context = RuntimeEnvironment.getApplication())
    }

    @Test
    fun `given no prior initialization, when initializing, do not throw any exception`() {
        ParselyTracker.init(siteId = "", context = RuntimeEnvironment.getApplication())
    }

    @Test
    fun `given tracker initialized, when calling a method, do not throw any exception`() {
        ParselyTracker.init(siteId = "", context = RuntimeEnvironment.getApplication())

        ParselyTracker.sharedInstance().startEngagement("url")
    }

    @After
    fun tearDown() {
        ParselyTracker.tearDown()
    }
}
