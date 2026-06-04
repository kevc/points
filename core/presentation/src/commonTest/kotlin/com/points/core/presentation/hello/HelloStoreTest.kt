package com.points.core.presentation.hello

import com.arkivanov.mvikotlin.core.utils.isAssertOnMainThreadEnabled
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.points.core.domain.Greeting
import com.points.core.domain.GreetUseCase
import kotlinx.coroutines.Dispatchers
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HelloStoreTest {

    @BeforeTest
    fun disableMainThreadAssertions() {
        // The store is exercised off the main thread in unit tests.
        isAssertOnMainThreadEnabled = false
    }

    @Test
    fun loadsGreetingFromUseCaseOnCreation() {
        val store = DefaultStoreFactory().helloStore(
            greet = GreetUseCase { Greeting("Hello from shared Kotlin") },
            mainContext = Dispatchers.Unconfined,
        )

        assertEquals("Hello from shared Kotlin", store.state.greeting)
    }
}
