package com.parsely.parselyandroid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
