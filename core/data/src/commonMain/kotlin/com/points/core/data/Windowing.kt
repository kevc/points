package com.points.core.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * The live current time zone. Injected as a function (not a captured [TimeZone]) so day-windowed reads honor
 * the system zone as it is *now*, not as it was at DI setup — the timezone half of issue #108. Production wires
 * `TimeZone.Companion::currentSystemDefault`; tests pass a fixed zone.
 */
typealias ZoneProvider = () -> TimeZone

/**
 * Emits `Unit` immediately, then again just after each local midnight, so day-windowed flows re-derive "today"
 * and roll live instead of being pinned to the day they were first collected — the window half of issue #108.
 * The zone is read per iteration via [zone], so the next tick already reflects a mid-run timezone change.
 *
 * Never completes; combine it with the data flow so the combined flow's lifetime is the collector's. Tests
 * that don't exercise the roll pass a finite ticker (e.g. `flowOf(Unit)`) so `combine` still emits on data
 * changes without an open timer.
 */
fun dayTicks(clock: Clock, zone: ZoneProvider): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        val now = clock.now()
        val z = zone()
        val nextMidnight = now.toLocalDateTime(z).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(z)
        delay((nextMidnight - now).inWholeMilliseconds.coerceAtLeast(1L))
    }
}
