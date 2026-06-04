package com.points.core.presentation.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Platform IO dispatcher (real `Dispatchers.IO` on JVM/Android; `Default` on iOS). */
internal expect fun ioDispatcher(): CoroutineDispatcher

/**
 * Coroutine dispatchers, injected by qualifier so callees own main-safety.
 * Tests swap this for [testDispatcherModule]. `named("io")` is consumed by
 * use-case factories (`withContext`); `named("main")` is the MVIKotlin/UI context.
 */
val dispatcherModule = module {
    single<CoroutineDispatcher>(named("io")) { ioDispatcher() }
    single<CoroutineDispatcher>(named("main")) { Dispatchers.Main }
}
