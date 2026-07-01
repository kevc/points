package com.points.android

import android.app.Application
import com.points.core.presentation.di.initKoin
import org.koin.android.ext.koin.androidContext

/** Starts Koin with the shared modules on launch, supplying the Android context the data layer needs. */
class PointsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@PointsApplication)
        }
    }
}
