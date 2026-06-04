package com.points.core.domain

/**
 * A simple domain entity carrying a greeting message.
 *
 * Used to prove the shared Kotlin layer is wired up end-to-end across platforms.
 */
data class Greeting(val message: String)

/**
 * Produces a [Greeting].
 *
 * Modelled as a named `fun interface` (never a `typealias`) so each injected use case erases to a
 * distinct type and DI bindings never collide.
 */
fun interface GreetUseCase {
    suspend operator fun invoke(): Greeting
}

/**
 * Default, pure [GreetUseCase]: no dispatcher, no infrastructure. Main-safety (if ever needed) is the
 * concern of an outer-module factory, not the domain.
 */
fun greetUseCase(): GreetUseCase = GreetUseCase { Greeting("Hello from shared Kotlin") }
