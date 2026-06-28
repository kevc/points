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
import com.points.core.presentation.edit.CreateEditComponent
import com.points.core.presentation.home.HomeComponent
import com.points.core.presentation.sync.SyncComponent
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Root of the Decompose tree. A [childStack] drives navigation: [Child.Home] (the M4 grid) is the initial
 * screen; tapping a tile pushes [Child.Detail] (the M2 [CounterComponent] for that type, the interim detail
 * until M5); the home FAB or a detail's edit affordance pushes [Child.Create] (the create/edit form, which
 * pops itself on save/cancel). The [SyncComponent] sits outside the stack as a persistent overlay.
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
        class Create(val component: CreateEditComponent) : Child
    }
}

@OptIn(ExperimentalUuidApi::class)
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val home: (ComponentContext, onOpen: (Uuid) -> Unit, onCreate: () -> Unit) -> HomeComponent,
    private val detail: (ComponentContext, pointTypeId: Uuid, onBack: () -> Unit, onEdit: () -> Unit) -> CounterComponent,
    private val create: (ComponentContext, editId: Uuid?, onSaved: () -> Unit, onCancel: () -> Unit) -> CreateEditComponent,
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
                home(
                    context,
                    { typeId -> navigation.pushNew(Config.Detail(typeId)) },
                    { navigation.pushNew(Config.Create(editId = null)) },
                ),
            )
            is Config.Detail -> RootComponent.Child.Detail(
                detail(
                    context,
                    config.pointTypeId,
                    { navigation.pop() },
                    { navigation.pushNew(Config.Create(editId = config.pointTypeId)) },
                ),
            )
            is Config.Create -> RootComponent.Child.Create(
                create(context, config.editId, { navigation.pop() }, { navigation.pop() }),
            )
        }

    private sealed interface Config {
        data object Home : Config
        data class Detail(val pointTypeId: Uuid) : Config
        data class Create(val editId: Uuid?) : Config
    }
}
