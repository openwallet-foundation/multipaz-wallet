package org.multipaz.wallet.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import react.useEffect
import react.useState

fun <T> useFlow(flow: StateFlow<T>): T {
    val (state, setState) = useState(flow.value)
    useEffect(flow) {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            flow.collect { setState(it) }
        }
        Cleanup {
            scope.cancel()
        }
    }
    return state
}
