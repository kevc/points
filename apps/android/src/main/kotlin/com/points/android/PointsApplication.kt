package com.points.android

import android.app.Application
import com.points.core.presentation.di.initKoin

/** Starts Koin with the shared modules on app launch. */
class PointsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}
