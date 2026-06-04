package com.points.core.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.points.core.domain.GreetUseCase
import com.points.core.domain.greetUseCase
import com.points.core.presentation.hello.DefaultHelloComponent
import com.points.core.presentation.hello.HelloComponent
import com.points.core.presentation.root.DefaultRootComponent
import com.points.core.presentation.root.RootComponent
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Presentation DI: binds the domain use case (the domain stays Koin-free; outer
 * modules wire its use cases), the MVIKotlin store factory, and the Decompose
 * components. Components take their `ComponentContext` as a runtime parameter.
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

    factory<RootComponent> { (componentContext: ComponentContext) ->
        DefaultRootComponent(componentContext) { childContext ->
            get<HelloComponent> { parametersOf(childContext) }
        }
    }
}

/** All client Koin modules, assembled at startup. */
fun pointsModules(): List<Module> = listOf(dispatcherModule, presentationModule)
