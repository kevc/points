package com.points.core.presentation.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Shared Koin entry point. Android calls this from `Application.onCreate`
 * (optionally passing `androidContext`); iOS invokes it from its app start.
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        appDeclaration()
        modules(pointsModules())
    }
