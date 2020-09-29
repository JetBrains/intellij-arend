package org.arend.typechecking

import com.intellij.openapi.util.ModificationTracker
import org.arend.typechecking.computation.CancellationIndicator

class ModificationCancellationIndicator(private val tracker: ModificationTracker) : CancellationIndicator {
    private var modCount = tracker.modificationCount

    override fun isCanceled() = tracker.modificationCount > modCount

    override fun cancel() {
        modCount--
    }
}