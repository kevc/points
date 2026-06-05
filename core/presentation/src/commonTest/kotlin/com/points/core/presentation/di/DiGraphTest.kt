package com.points.core.presentation.di

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.core.utils.isAssertOnMainThreadEnabled
import com.points.core.domain.DecrementPoint
import com.points.core.domain.GreetUseCase
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObservePointValue
import com.points.core.domain.PointEvent
import com.points.core.presentation.root.RootComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.Instant
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Dispatcher module swapped in for tests so `withContext`/store run synchronously. */
private val testDispatcherModule = module {
    single<CoroutineDispatcher>(named("io")) { UnconfinedTestDispatcher() }
    single<CoroutineDispatcher>(named("main")) { UnconfinedTestDispatcher() }
}

/**
 * Stand-in for the real data module: fake use cases so the component graph resolves without a SQL
 * driver or HTTP engine (those need a platform). [ObservePointValue] reports a fixed value to prove
 * the counter store wires the observed flow into its state.
 */
@OptIn(ExperimentalUuidApi::class)
private val fakeDataModule = module {
    factory<IncrementPoint> { IncrementPoint { id, delta -> fakeEvent(id, delta) } }
    factory<DecrementPoint> { DecrementPoint { id, delta -> fakeEvent(id, -delta) } }
    factory<ObservePointValue> { ObservePointValue { flowOf(7L) } }
}

@OptIn(ExperimentalUuidApi::class)
private fun fakeEvent(pointTypeId: Uuid, delta: Long) =
    PointEvent(Uuid.random(), pointTypeId, delta, "device-test", Instant.fromEpochSeconds(0))

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
    fun resolvesGraphAndRendersComponents() {
        val koin = startKoin {
            modules(presentationModule, testDispatcherModule, fakeDataModule)
        }.koin

        // Plain singletons resolve.
        assertNotNull(koin.get<GreetUseCase>())
        assertNotNull(koin.get<StoreFactory>())

        // The parameterized component graph resolves and both children render.
        val context = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val root = koin.get<RootComponent> { parametersOf(context) }

        assertEquals("Hello from shared Kotlin", root.hello.state.value.greeting)
        assertEquals(7L, root.counter.state.value.value)
    }
}
