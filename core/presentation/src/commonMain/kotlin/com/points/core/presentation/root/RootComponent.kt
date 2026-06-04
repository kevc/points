package com.points.core.presentation.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.points.core.presentation.hello.HelloComponent

/** Root of the Decompose tree. For M1 it hosts a single [HelloComponent]. */
interface RootComponent {
    val hello: HelloComponent
}

class DefaultRootComponent(
    componentContext: ComponentContext,
    hello: (ComponentContext) -> HelloComponent,
) : RootComponent, ComponentContext by componentContext {

    override val hello: HelloComponent = hello(childContext(key = "hello"))
}
