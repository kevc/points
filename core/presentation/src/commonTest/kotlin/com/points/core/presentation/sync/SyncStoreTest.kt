package com.points.core.presentation.sync

import com.arkivanov.mvikotlin.core.utils.isAssertOnMainThreadEnabled
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.points.core.domain.ObserveSyncStatus
import com.points.core.domain.SyncPointEvents
import com.points.core.domain.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncStoreTest {

    @BeforeTest
    fun disableMainThreadAssertions() {
        isAssertOnMainThreadEnabled = false
    }

    /** Counts reconcile triggers so we can assert the once-on-creation and foreground-intent passes. */
    private class RecordingSync : SyncPointEvents {
        var calls = 0
            private set
        override suspend fun invoke() { calls++ }
    }

    @Test
    fun reflectsObservedStatusInState() = runTest {
        val status = MutableStateFlow(SyncStatus.Idle)
        val store = DefaultStoreFactory().syncStore(
            observeSyncStatus = ObserveSyncStatus { status },
            syncPointEvents = RecordingSync(),
            mainContext = UnconfinedTestDispatcher(),
        )

        assertEquals(SyncStatus.Idle, store.state.status)

        status.value = SyncStatus.Syncing
        assertEquals(SyncStatus.Syncing, store.state.status)

        status.value = SyncStatus.Synced
        assertEquals(SyncStatus.Synced, store.state.status)
    }

    @Test
    fun triggersOneReconcileOnCreation() = runTest {
        val sync = RecordingSync()
        DefaultStoreFactory().syncStore(
            observeSyncStatus = ObserveSyncStatus { emptyFlow() },
            syncPointEvents = sync,
            mainContext = UnconfinedTestDispatcher(),
        )

        assertEquals(1, sync.calls)
    }

    @Test
    fun syncIntentTriggersAnotherReconcile() = runTest {
        val sync = RecordingSync()
        val store = DefaultStoreFactory().syncStore(
            observeSyncStatus = ObserveSyncStatus { emptyFlow() },
            syncPointEvents = sync,
            mainContext = UnconfinedTestDispatcher(),
        )
        assertEquals(1, sync.calls) // creation

        store.accept(SyncStore.Intent.Sync)
        assertEquals(2, sync.calls)
    }
}
