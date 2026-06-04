package com.points.core.presentation.hello

import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import com.points.core.domain.GreetUseCase
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** State of the hello screen: the shared greeting text, empty until loaded. */
interface HelloStore : Store<Nothing, HelloStore.State, Nothing> {
    data class State(val greeting: String = "")
}

private sealed interface Msg {
    data class GreetingLoaded(val greeting: String) : Msg
}

/**
 * Builds the [HelloStore]. The store loads the greeting once on creation via the
 * injected [GreetUseCase]. [mainContext] is the MVIKotlin main coroutine context
 * (Dispatchers.Main in production; a test dispatcher in tests).
 */
internal fun StoreFactory.helloStore(
    greet: GreetUseCase,
    mainContext: CoroutineContext,
): HelloStore =
    object : HelloStore, Store<Nothing, HelloStore.State, Nothing> by create(
        name = "HelloStore",
        initialState = HelloStore.State(),
        bootstrapper = SimpleBootstrapper(Unit),
        executorFactory = coroutineExecutorFactory<Nothing, Unit, HelloStore.State, Msg, Nothing>(mainContext) {
            onAction<Unit> {
                launch {
                    val greeting = greet()
                    dispatch(Msg.GreetingLoaded(greeting.message))
                }
            }
        },
        reducer = { msg ->
            when (msg) {
                is Msg.GreetingLoaded -> copy(greeting = msg.greeting)
            }
        },
    ) {}
