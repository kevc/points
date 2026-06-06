@file:OptIn(ExperimentalUuidApi::class)

package com.points.core.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.points.core.data.di.dataModule
import com.points.core.data.di.platformDataModule
import com.points.core.domain.GreetUseCase
import com.points.core.domain.greetUseCase
import com.points.core.presentation.counter.CounterComponent
import com.points.core.presentation.counter.DEFAULT_POINT_TYPE_ID
import com.points.core.presentation.counter.DefaultCounterComponent
import com.points.core.presentation.hello.DefaultHelloComponent
import com.points.core.presentation.hello.HelloComponent
import com.points.core.presentation.root.DefaultRootComponent
import com.points.core.presentation.root.RootComponent
import com.points.core.presentation.sync.DefaultSyncComponent
import com.points.core.presentation.sync.SyncComponent
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.uuid.ExperimentalUuidApi

/**
 * Presentation DI: the MVIKotlin store factory and the Decompose components. The counter component
 * injects the individual use cases (bound in [dataModule]) — never the repository. Components take
 * their `ComponentContext` as a runtime parameter.
 */
val presentationModule = module {
    factory<GreetUseCase> { greetUseCase() }
    single<StoreFactory> { DefaultStoreFactory() }

    factory<HelloComponent> { (componentContext: ComponentContext) ->
        DefaultHelloComponent(
            componentContext = componentContext,
            storeFactory = get(),
            greet = get(),
            mainContext = get<CoroutineDispatcher>(named("main")),
        )
    }

    factory<CounterComponent> { (componentContext: ComponentContext) ->
        DefaultCounterComponent(
            componentContext = componentContext,
            storeFactory = get(),
            pointTypeId = DEFAULT_POINT_TYPE_ID,
            increment = get(),
            decrement = get(),
            observeValue = get(),
            mainContext = get<CoroutineDispatcher>(named("main")),
        )
    }

    factory<SyncComponent> { (componentContext: ComponentContext) ->
        DefaultSyncComponent(
            componentContext = componentContext,
            storeFactory = get(),
            observeSyncStatus = get(),
            syncPointEvents = get(),
            mainContext = get<CoroutineDispatcher>(named("main")),
        )
    }

    factory<RootComponent> { (componentContext: ComponentContext) ->
        DefaultRootComponent(
            componentContext,
            hello = { childContext -> get<HelloComponent> { parametersOf(childContext) } },
            counter = { childContext -> get<CounterComponent> { parametersOf(childContext) } },
            sync = { childContext -> get<SyncComponent> { parametersOf(childContext) } },
        )
    }
}

/** All client Koin modules, assembled at startup. */
fun pointsModules(): List<Module> =
    listOf(dispatcherModule, dataModule, platformDataModule, presentationModule)
