package com.points.core.presentation.di

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.core.utils.isAssertOnMainThreadEnabled
import com.points.core.domain.GreetUseCase
import com.points.core.presentation.root.RootComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Dispatcher module swapped in for tests so `withContext`/store run synchronously. */
private val testDispatcherModule = module {
    single<CoroutineDispatcher>(named("io")) { UnconfinedTestDispatcher() }
    single<CoroutineDispatcher>(named("main")) { UnconfinedTestDispatcher() }
}

class DiGraphTest {

    @BeforeTest
    fun setup() {
        isAssertOnMainThreadEnabled = false
    }

    @AfterTest
    fun teardown() {
        stopKoin()
    }

    @Test
    fun resolvesGraphAndRendersGreetingThroughComponents() {
        val koin = startKoin {
            modules(presentationModule, testDispatcherModule)
        }.koin

        // Plain singletons resolve.
        assertNotNull(koin.get<GreetUseCase>())
        assertNotNull(koin.get<StoreFactory>())

        // The parameterized component graph resolves and renders the shared greeting.
        val context = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val root = koin.get<RootComponent> { parametersOf(context) }

        assertEquals("Hello from shared Kotlin", root.hello.state.value.greeting)
    }
}
