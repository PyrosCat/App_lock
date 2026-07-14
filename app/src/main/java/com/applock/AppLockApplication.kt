package com.applock

import android.app.Application
import com.applock.core.Graph

class AppLockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
