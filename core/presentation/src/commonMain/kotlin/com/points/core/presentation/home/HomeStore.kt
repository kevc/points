package com.points.core.presentation.home

import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import com.points.core.domain.ObserveTiles
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointTile
import com.points.core.domain.RingState
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * State of the home grid: one [HomeTile] per active point type. The store owns the user-facing **copy** (the
 * meta caption and grouped value text), formatted from the pure [PointTile] data, so Android and iOS render
 * identical strings without duplicating the logic. There are no UI intents — opening a tile is a navigation
 * output handled by the component, not store state.
 */
interface HomeStore : Store<Nothing, HomeStore.State, Nothing> {
    /**
     * @property loaded false until the first emission arrives, so the UI can tell "still loading" from a
     *   genuinely empty catalog (and only then show the first-run prompt).
     */
    data class State(val tiles: List<HomeTile> = emptyList(), val loaded: Boolean = false)
}

/**
 * A render-ready home tile.
 *
 * @property id the point type's id, passed back on tap to open its detail.
 * @property valueText the current value, grouped (e.g. "4,030").
 * @property meta the caption under the value ("→ 5,000", "5 / 8 today", "5d calm").
 * @property ring the gauge state (progress, ticks, accent, over).
 * @property hue the point's accent (color-wheel degrees) — the platform maps it to its hue palette.
 */
@OptIn(ExperimentalUuidApi::class)
data class HomeTile(
    val id: Uuid,
    val name: String,
    val valueText: String,
    val unit: String,
    val meta: String,
    val ring: RingState,
    val hue: Int,
    val step: Long,
)

private sealed interface Msg {
    data class TilesUpdated(val tiles: List<HomeTile>) : Msg
}

/**
 * Builds the [HomeStore]: on creation it observes [ObserveTiles] and maps each [PointTile] to a render-ready
 * [HomeTile]. The grid reflects the ledger + catalog reactively; it never optimistically guesses.
 */
internal fun StoreFactory.homeStore(
    observeTiles: ObserveTiles,
    mainContext: CoroutineContext,
): HomeStore =
    object : HomeStore, Store<Nothing, HomeStore.State, Nothing> by create(
        name = "HomeStore",
        initialState = HomeStore.State(),
        bootstrapper = SimpleBootstrapper(Unit),
        executorFactory = coroutineExecutorFactory<Nothing, Unit, HomeStore.State, Msg, Nothing>(mainContext) {
            onAction<Unit> {
                launch {
                    observeTiles().collect { tiles -> dispatch(Msg.TilesUpdated(tiles.map { it.toHomeTile() })) }
                }
            }
        },
        reducer = { msg ->
            when (msg) {
                is Msg.TilesUpdated -> copy(tiles = msg.tiles, loaded = true)
            }
        },
    ) {}

@OptIn(ExperimentalUuidApi::class)
private fun PointTile.toHomeTile(): HomeTile = HomeTile(
    id = type.id,
    name = type.name,
    valueText = groupThousands(value),
    unit = type.unit,
    meta = metaCaption(),
    ring = ring,
    hue = type.hue,
    step = type.step,
)

/** The caption under a tile's value — tuned per mode/tone so it encourages rather than scores. */
private fun PointTile.metaCaption(): String {
    val target = type.target
    return when {
        type.mode == PointMode.DAILY && target != null -> "$value / $target today"
        type.goal == PointGoal.DOWN -> when {
            daysSinceLast <= 0 -> "today"
            daysSinceLast >= 999 -> "calm" // includes the "never active" sentinel
            else -> "${daysSinceLast}d calm"
        }
        else -> "→ ${groupThousands(climbingNext)}"
    }
}

/** Thousands grouping with ',' — commonMain has no locale number formatter, and the design groups with commas. */
internal fun groupThousands(n: Long): String {
    val negative = n < 0
    val digits = (if (negative) -n else n).toString()
    val sb = StringBuilder()
    for (i in digits.indices) {
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
        sb.append(digits[i])
    }
    return if (negative) "-$sb" else sb.toString()
}
