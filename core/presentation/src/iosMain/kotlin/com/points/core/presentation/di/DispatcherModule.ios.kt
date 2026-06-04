package com.points.core.presentation.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// iOS/Kotlin-Native has no dedicated IO dispatcher; Default is the pragmatic choice.
internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.Default
