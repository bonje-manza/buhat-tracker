package com.buhat.tracker

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class BuhatApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch { container.repository.seedIfNeeded() }
    }

    // Test-only hook (see .opencode/skills/e2e-testing, Isolation): swap in an in-memory
    // database so a UI test starts from a known state, then seed it synchronously.
    fun resetForTest() {
        container.close()
        container = AppContainer(this, inMemory = true)
        runBlocking { container.repository.seedIfNeeded() }
    }
}
