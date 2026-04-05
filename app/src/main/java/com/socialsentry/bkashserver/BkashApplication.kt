package com.socialsentry.bkashserver

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application class that provides a single, lifecycle-aware CoroutineScope for all
 * background work (SMS processing, uploads). This replaces all GlobalScope.launch usages
 * so coroutines are properly managed and can't cause the CPU to spin indefinitely.
 */
class BkashApplication : Application() {
    /**
     * This scope lives as long as the entire application process.
     * SupervisorJob ensures one failed coroutine doesn't cancel others.
     * Dispatchers.Default is used as default; callers switch to IO when needed.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
