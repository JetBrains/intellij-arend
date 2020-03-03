package org.arend.typechecking

import com.intellij.openapi.progress.ProgressIndicator
import org.arend.typechecking.computation.CancellationIndicator


class ArendCancellationIndicator(val progress: ProgressIndicator) : CancellationIndicator {
    override fun isCanceled() = progress.isCanceled
}