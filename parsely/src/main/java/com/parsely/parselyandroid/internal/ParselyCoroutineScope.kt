package com.parsely.parselyandroid.internal

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal val sdkScope =
    CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("Parse.ly SDK Scope"))
