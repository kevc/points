@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.points.android

import android.app.Application
import android.content.pm.ApplicationInfo
import com.points.core.domain.CreatePointType
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObservePointTypes
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointTypeDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * TEMPORARY (remove at #86 — empty/first-run state): on a **debuggable** build with an empty catalog, seed the
 * design's six sample point types (and a representative count for each) so the home grid is demoable before
 * Create (#83) exists. A no-op in release builds and once any type exists.
 */
internal fun Application.seedSampleDataIfDebuggable() {
    val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!debuggable) return

    val koin = GlobalContext.get()
    val observeTypes = koin.get<ObservePointTypes>()
    val create = koin.get<CreatePointType>()
    val increment = koin.get<IncrementPoint>()

    CoroutineScope(Dispatchers.Default).launch {
        if (observeTypes().first().isNotEmpty()) return@launch
        SAMPLES.forEach { (draft, seedCount) ->
            val type = create(draft)
            if (seedCount != 0L) increment(type.id, seedCount)
        }
    }
}

private val SAMPLES: List<Pair<PointTypeDraft, Long>> = listOf(
    PointTypeDraft("Days smoke-free", hue = 152, icon = "leaf", unit = "days") to 42L,
    PointTypeDraft("Water", hue = 215, icon = "drop", mode = PointMode.DAILY, target = 8, unit = "glasses") to 5L,
    PointTypeDraft("Meditation", hue = 285, icon = "spark", unit = "sessions") to 182L,
    PointTypeDraft("Push-ups", hue = 40, icon = "bolt", step = 10, unit = "reps") to 4030L,
    PointTypeDraft("Times angry", hue = 18, icon = "pulse", goal = PointGoal.DOWN) to 12L,
    PointTypeDraft("Books read", hue = 330, icon = "book") to 5L,
)
