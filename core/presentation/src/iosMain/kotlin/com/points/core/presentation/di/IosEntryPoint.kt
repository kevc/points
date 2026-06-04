package com.points.core.presentation.di

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.points.core.presentation.root.RootComponent
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform

/** Starts Koin from the iOS app (no Swift-side lambda required). */
fun startKoinIos() {
    initKoin()
}

/**
 * Builds the shared [RootComponent] for the iOS app, resolving it from Koin with a
 * fresh Decompose [DefaultComponentContext]. Call once at app start and retain it.
 */
fun createRootComponent(): RootComponent =
    KoinPlatform.getKoin().get<RootComponent> {
        parametersOf(DefaultComponentContext(lifecycle = LifecycleRegistry()))
    }
