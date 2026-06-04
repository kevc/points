package com.points.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GreetUseCaseTest {

    @Test
    fun returnsHelloFromSharedKotlin() = runTest {
        // Exercise the use case through the fun interface port, not the concrete impl.
        val greet: GreetUseCase = greetUseCase()

        val greeting = greet()

        assertEquals(Greeting("Hello from shared Kotlin"), greeting)
        assertEquals("Hello from shared Kotlin", greeting.message)
    }
}
