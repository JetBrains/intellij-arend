package org.arend.typechecking

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.arend.typechecking.computation.CancellationIndicator

class CoroutineCancellationIndicator(private val coroutineScope: CoroutineScope) : CancellationIndicator {
    override fun isCanceled() = !coroutineScope.isActive

    override fun cancel() {
        coroutineScope.cancel()
    }
}