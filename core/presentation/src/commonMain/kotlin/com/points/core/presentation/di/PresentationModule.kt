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
import com.points.core.presentation.counter.DefaultCounterComponent
import com.points.core.presentation.edit.CreateEditComponent
import com.points.core.presentation.edit.DefaultCreateEditComponent
import com.points.core.presentation.hello.DefaultHelloComponent
import com.points.core.presentation.hello.HelloComponent
import com.points.core.presentation.home.DefaultHomeComponent
import com.points.core.presentation.home.HomeComponent
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
import kotlin.uuid.Uuid

/**
 * Presentation DI: the MVIKotlin store factory and the Decompose components. Components inject individual use
 * cases (bound in [dataModule]) — never a repository. Each component takes its `ComponentContext` plus any
 * navigation callbacks as runtime parameters (`parametersOf`); the root wires those callbacks to the stack.
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

    factory<HomeComponent> { (componentContext: ComponentContext, onOpen: (Uuid) -> Unit, onCreate: () -> Unit) ->
        DefaultHomeComponent(
            componentContext = componentContext,
            storeFactory = get(),
            observeTiles = get(),
            increment = get(),
            decrement = get(),
            create = get(),
            mainContext = get<CoroutineDispatcher>(named("main")),
            onOpenType = onOpen,
            onCreateType = onCreate,
        )
    }

    factory<CounterComponent> {
            (componentContext: ComponentContext, pointTypeId: Uuid, onBack: () -> Unit, onEdit: () -> Unit) ->
        DefaultCounterComponent(
            componentContext = componentContext,
            storeFactory = get(),
            pointTypeId = pointTypeId,
            increment = get(),
            decrement = get(),
            observeValue = get(),
            reset = get(),
            delete = get(),
            restore = get(),
            mainContext = get<CoroutineDispatcher>(named("main")),
            onBackPressed = onBack,
            onEditRequested = onEdit,
        )
    }

    factory<CreateEditComponent> {
            (componentContext: ComponentContext, editId: Uuid?, onSaved: () -> Unit, onCancel: () -> Unit) ->
        DefaultCreateEditComponent(
            componentContext = componentContext,
            storeFactory = get(),
            editId = editId,
            create = get(),
            edit = get(),
            observeTypes = get(),
            mainContext = get<CoroutineDispatcher>(named("main")),
            onSaved = onSaved,
            onCancelled = onCancel,
        )
    }

    factory<CreateEditComponent> {
            (componentContext: ComponentContext, editId: Uuid?, onSaved: () -> Unit, onCancel: () -> Unit) ->
        DefaultCreateEditComponent(
            componentContext = componentContext,
            storeFactory = get(),
            editId = editId,
            create = get(),
            edit = get(),
            observeTypes = get(),
            mainContext = get<CoroutineDispatcher>(named("main")),
            onSaved = onSaved,
            onCancelled = onCancel,
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
            home = { childContext, onOpen, onCreate ->
                get<HomeComponent> { parametersOf(childContext, onOpen, onCreate) }
            },
            detail = { childContext, typeId, onBack, onEdit ->
                get<CounterComponent> { parametersOf(childContext, typeId, onBack, onEdit) }
            },
            create = { childContext, editId, onSaved, onCancel ->
                get<CreateEditComponent> { parametersOf(childContext, editId, onSaved, onCancel) }
            },
            sync = { childContext -> get<SyncComponent> { parametersOf(childContext) } },
        )
    }
}

/** All client Koin modules, assembled at startup. */
fun pointsModules(): List<Module> =
    listOf(dispatcherModule, dataModule, platformDataModule, presentationModule)
