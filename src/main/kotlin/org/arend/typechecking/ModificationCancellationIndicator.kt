package org.arend.typechecking

import com.intellij.openapi.util.ModificationTracker
import org.arend.typechecking.computation.CancellationIndicator

class ModificationCancellationIndicator(private val tracker: ModificationTracker, private var modCount: Long = tracker.modificationCount) : CancellationIndicator {
    override fun isCanceled() = tracker.modificationCount > modCount

    override fun cancel() {
        modCount--
    }
}