package com.points.core.presentation.edit

import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import com.points.core.domain.CreatePointType
import com.points.core.domain.EditPointType
import com.points.core.domain.ObservePointTypes
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointType
import com.points.core.domain.PointTypeDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Form state for creating or editing a point type. Mirrors the design's "New point" form: name, color (hue),
 * how it's counted (mode), step, an optional daily target (0 = Off), and a non-judgmental tone for cumulative
 * types. When [State.editId] is set the form is pre-filled from the existing type (loaded on creation) and
 * [Intent.Save] edits it; otherwise it creates a new one. Saving emits [Label.Saved] for the caller to
 * navigate back.
 */
@OptIn(ExperimentalUuidApi::class)
interface CreateEditStore : Store<CreateEditStore.Intent, CreateEditStore.State, CreateEditStore.Label> {
    sealed interface Intent {
        data class SetName(val name: String) : Intent
        data class SetHue(val hue: Int) : Intent
        data class SetMode(val mode: PointMode) : Intent
        data class SetGoal(val goal: PointGoal) : Intent
        data object StepUp : Intent
        data object StepDown : Intent
        data object TargetUp : Intent
        data object TargetDown : Intent
        data object Save : Intent
    }

    data class State(
        val editId: Uuid? = null,
        val name: String = "",
        val hue: Int = 152,
        val mode: PointMode = PointMode.CUMULATIVE,
        val step: Long = 1,
        val target: Long = 0, // 0 = Off (no daily target)
        val goal: PointGoal = PointGoal.UP,
        val icon: String = "spark",
        val unit: String = "",
    ) {
        val editing: Boolean get() = editId != null
    }

    sealed interface Label {
        /** The save completed (created or edited); the caller should dismiss the form. */
        data object Saved : Label
    }
}

private sealed interface Msg {
    data class Loaded(val type: PointType) : Msg
    data class Name(val name: String) : Msg
    data class Hue(val hue: Int) : Msg
    data class Mode(val mode: PointMode) : Msg
    data class Goal(val goal: PointGoal) : Msg
    data class Step(val step: Long) : Msg
    data class Target(val target: Long) : Msg
}

/**
 * Builds the [CreateEditStore]. If [editId] is non-null it loads that type once and pre-fills the form. The
 * step/target steppers follow the design's non-linear increments (step jumps by 5 at/above 10; target floors
 * at 0 = Off). [Intent.Save] builds a [PointTypeDraft] and calls [edit] or [create] via the injected use
 * cases, then publishes [CreateEditStore.Label.Saved].
 */
@OptIn(ExperimentalUuidApi::class)
internal fun StoreFactory.createEditStore(
    editId: Uuid?,
    create: CreatePointType,
    edit: EditPointType,
    observeTypes: ObservePointTypes,
    mainContext: CoroutineContext,
): CreateEditStore =
    object : CreateEditStore, Store<CreateEditStore.Intent, CreateEditStore.State, CreateEditStore.Label> by create(
        name = "CreateEditStore",
        initialState = CreateEditStore.State(editId = editId),
        bootstrapper = SimpleBootstrapper(Unit),
        executorFactory = coroutineExecutorFactory<CreateEditStore.Intent, Unit, CreateEditStore.State, Msg, CreateEditStore.Label>(mainContext) {
            onAction<Unit> {
                if (editId != null) {
                    launch {
                        observeTypes().first().firstOrNull { it.id == editId }?.let { dispatch(Msg.Loaded(it)) }
                    }
                }
            }
            onIntent<CreateEditStore.Intent.SetName> { dispatch(Msg.Name(it.name)) }
            onIntent<CreateEditStore.Intent.SetHue> { dispatch(Msg.Hue(it.hue)) }
            onIntent<CreateEditStore.Intent.SetMode> { dispatch(Msg.Mode(it.mode)) }
            onIntent<CreateEditStore.Intent.SetGoal> { dispatch(Msg.Goal(it.goal)) }
            onIntent<CreateEditStore.Intent.StepUp> {
                val step = state().step
                dispatch(Msg.Step(step + if (step >= 10) 5 else 1))
            }
            onIntent<CreateEditStore.Intent.StepDown> {
                val step = state().step
                dispatch(Msg.Step(maxOf(1L, step - if (step > 10) 5 else 1)))
            }
            onIntent<CreateEditStore.Intent.TargetUp> { dispatch(Msg.Target(state().target + 1)) }
            onIntent<CreateEditStore.Intent.TargetDown> { dispatch(Msg.Target(maxOf(0L, state().target - 1))) }
            onIntent<CreateEditStore.Intent.Save> {
                val s = state()
                val draft = PointTypeDraft(
                    name = s.name.trim().ifEmpty { "Untitled" },
                    hue = s.hue,
                    icon = s.icon,
                    mode = s.mode,
                    step = maxOf(1L, s.step),
                    goal = if (s.mode == PointMode.DAILY) PointGoal.UP else s.goal,
                    target = if (s.mode == PointMode.DAILY && s.target > 0) s.target else null,
                    unit = s.unit,
                )
                val id = s.editId
                launch {
                    if (id != null) edit(id, draft) else create(draft)
                    publish(CreateEditStore.Label.Saved)
                }
            }
        },
        reducer = { msg ->
            when (msg) {
                is Msg.Loaded -> copy(
                    editId = msg.type.id, name = msg.type.name, hue = msg.type.hue, mode = msg.type.mode,
                    step = msg.type.step, target = msg.type.target ?: 0, goal = msg.type.goal,
                    icon = msg.type.icon, unit = msg.type.unit,
                )
                is Msg.Name -> copy(name = msg.name)
                is Msg.Hue -> copy(hue = msg.hue)
                is Msg.Mode -> copy(mode = msg.mode)
                is Msg.Goal -> copy(goal = msg.goal)
                is Msg.Step -> copy(step = msg.step)
                is Msg.Target -> copy(target = msg.target)
            }
        },
    ) {}
