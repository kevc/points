package com.points.core.presentation.hello

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.states
import com.points.core.domain.GreetUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** Shared component backing the hello screen; exposes the greeting as a Decompose [Value]. */
interface HelloComponent {
    val state: Value<HelloStore.State>
}

class DefaultHelloComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    greet: GreetUseCase,
    mainContext: CoroutineContext,
) : HelloComponent, ComponentContext by componentContext {

    private val store = instanceKeeper.getStore { storeFactory.helloStore(greet, mainContext) }

    private val scope = CoroutineScope(SupervisorJob() + mainContext)
        .also { scope -> lifecycle.doOnDestroy(scope::cancel) }

    override val state: Value<HelloStore.State> =
        MutableValue(store.state).also { value ->
            scope.launch { store.states.collect { value.value = it } }
        }
}
