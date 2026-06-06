package com.points.core.domain

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncStatusTest {

    @Test
    fun modelsTheFullLifecycle() {
        assertEquals(
            listOf("Idle", "Syncing", "Synced", "Offline", "Failed"),
            SyncStatus.entries.map { it.name },
        )
    }

    @Test
    fun observeSyncStatusStreamsThroughThePort() = runTest {
        val emissions = listOf(SyncStatus.Idle, SyncStatus.Syncing, SyncStatus.Synced)
        val observe = ObserveSyncStatus { flowOf(*emissions.toTypedArray()) }

        assertEquals(emissions, observe().toList())
    }
}
