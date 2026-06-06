package com.points.core.presentation.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.points.core.presentation.counter.CounterComponent
import com.points.core.presentation.hello.HelloComponent

/** Root of the Decompose tree. Hosts the [CounterComponent] (M2) alongside the [HelloComponent] (M1). */
interface RootComponent {
    val hello: HelloComponent
    val counter: CounterComponent
}

class DefaultRootComponent(
    componentContext: ComponentContext,
    hello: (ComponentContext) -> HelloComponent,
    counter: (ComponentContext) -> CounterComponent,
) : RootComponent, ComponentContext by componentContext {

    override val hello: HelloComponent = hello(childContext(key = "hello"))
    override val counter: CounterComponent = counter(childContext(key = "counter"))
}
