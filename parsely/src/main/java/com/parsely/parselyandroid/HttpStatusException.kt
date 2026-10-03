package com.parsely.parselyandroid

import java.io.IOException

/** The server answered, but with an error status. */
internal class HttpStatusException(val status: Int) : IOException("Server returned HTTP $status")
