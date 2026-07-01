package com.points.core.presentation.edit

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.extensions.coroutines.states
import com.points.core.domain.CreatePointType
import com.points.core.domain.EditPointType
import com.points.core.domain.ObservePointTypes
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Backs the create/edit form. Exposes the form [state] and one setter per field plus [onSave]/[onCancel].
 * Construct with a null [editId] to create, or an existing type's id to edit (the form pre-fills). A
 * successful save invokes [onSaved] (the caller pops back); [onCancel] dismisses without saving.
 */
@OptIn(ExperimentalUuidApi::class)
interface CreateEditComponent {
    val state: Value<CreateEditStore.State>
    fun onName(value: String)
    fun onHue(hue: Int)
    fun onMode(mode: PointMode)
    fun onGoal(goal: PointGoal)
    fun onStepUp()
    fun onStepDown()
    fun onTargetUp()
    fun onTargetDown()
    fun onSave()
    fun onCancel()
}

@OptIn(ExperimentalUuidApi::class)
class DefaultCreateEditComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    editId: Uuid?,
    create: CreatePointType,
    edit: EditPointType,
    observeTypes: ObservePointTypes,
    mainContext: CoroutineContext,
    private val onSaved: () -> Unit,
    private val onCancelled: () -> Unit,
) : CreateEditComponent, ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        storeFactory.createEditStore(editId, create, edit, observeTypes, mainContext)
    }

    private val scope = CoroutineScope(SupervisorJob() + mainContext)
        .also { scope -> lifecycle.doOnDestroy(scope::cancel) }

    override val state: Value<CreateEditStore.State> =
        MutableValue(store.state).also { value ->
            scope.launch { store.states.collect { value.value = it } }
            scope.launch { store.labels.collect { if (it is CreateEditStore.Label.Saved) onSaved() } }
        }

    override fun onName(value: String) = store.accept(CreateEditStore.Intent.SetName(value))
    override fun onHue(hue: Int) = store.accept(CreateEditStore.Intent.SetHue(hue))
    override fun onMode(mode: PointMode) = store.accept(CreateEditStore.Intent.SetMode(mode))
    override fun onGoal(goal: PointGoal) = store.accept(CreateEditStore.Intent.SetGoal(goal))
    override fun onStepUp() = store.accept(CreateEditStore.Intent.StepUp)
    override fun onStepDown() = store.accept(CreateEditStore.Intent.StepDown)
    override fun onTargetUp() = store.accept(CreateEditStore.Intent.TargetUp)
    override fun onTargetDown() = store.accept(CreateEditStore.Intent.TargetDown)
    override fun onSave() = store.accept(CreateEditStore.Intent.Save)
    override fun onCancel() = onCancelled()
}
