package org.arend.typechecking

import com.intellij.openapi.progress.ProgressIndicator


class ArendCancellationIndicator(val progress: ProgressIndicator) : CancellationIndicator {
    override fun isCanceled() = progress.isCanceled
}