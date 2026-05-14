package com.parsely.parselyandroid

/**
 * The category of a conversion event. The [wireValue] of each case is the string sent to
 * Parse.ly over the wire and must match the values accepted by the Parse.ly conversions
 * backend. Use [CUSTOM] for conversions that don't fit one of the named categories.
 */
public enum class ConversionType(internal val wireValue: String) {
    NEWSLETTER_SIGNUP("newsletter_signup"),
    LEAD_CAPTURE("lead_capture"),
    LINK_CLICK("link_click"),
    SUBSCRIPTION("subscription"),
    PURCHASE("purchase"),
    CUSTOM("custom"),
}
