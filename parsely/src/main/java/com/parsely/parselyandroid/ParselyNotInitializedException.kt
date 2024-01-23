package com.parsely.parselyandroid

public class ParselyNotInitializedException() :
    Exception("Parse.ly client has not been initialized. Call ParselyTracker#init before using the SDK.")
