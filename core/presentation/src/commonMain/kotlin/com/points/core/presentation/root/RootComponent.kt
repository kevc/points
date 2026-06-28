package com.points.core.presentation.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.points.core.presentation.counter.CounterComponent
import com.points.core.presentation.home.HomeComponent
import com.points.core.presentation.sync.SyncComponent
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Root of the Decompose tree. A [childStack] drives navigation: [Child.Home] (the M4 grid) is the initial
 * screen, and tapping a tile pushes [Child.Detail] — the M2 [CounterComponent] parameterized by that type's
 * id (the interim per-type screen until M5's rich Detail). The [SyncComponent] sits outside the stack as a
 * persistent overlay.
 *
 * Navigation state is not serialized (no process-death restore yet); a cold start re-derives Home.
 */
@OptIn(ExperimentalUuidApi::class)
interface RootComponent {
    val stack: Value<ChildStack<*, Child>>
    val sync: SyncComponent

    sealed interface Child {
        class Home(val component: HomeComponent) : Child
        class Detail(val component: CounterComponent) : Child
    }
}

@OptIn(ExperimentalUuidApi::class)
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val home: (ComponentContext, onOpen: (Uuid) -> Unit) -> HomeComponent,
    private val detail: (ComponentContext, pointTypeId: Uuid, onBack: () -> Unit) -> CounterComponent,
    sync: (ComponentContext) -> SyncComponent,
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    override val sync: SyncComponent = sync(childContext(key = "sync"))

    override val stack: Value<ChildStack<*, RootComponent.Child>> =
        childStack(
            source = navigation,
            serializer = null,
            initialConfiguration = Config.Home,
            handleBackButton = true,
            childFactory = ::child,
        )

    private fun child(config: Config, context: ComponentContext): RootComponent.Child =
        when (config) {
            Config.Home -> RootComponent.Child.Home(
                home(context) { typeId -> navigation.pushNew(Config.Detail(typeId)) },
            )
            is Config.Detail -> RootComponent.Child.Detail(
                detail(context, config.pointTypeId) { navigation.pop() },
            )
        }

    private sealed interface Config {
        data object Home : Config
        data class Detail(val pointTypeId: Uuid) : Config
    }
}
