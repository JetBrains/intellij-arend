package org.arend.typechecking.runner

import com.intellij.platform.util.progress.RawProgressReporter
import org.arend.server.ProgressReporter

class IntellijProgressReporter<T>(private val reporter: RawProgressReporter, private val print : (T) -> String?) : ProgressReporter<T> {
    private var total = 0
    private var current = 0

    override fun beginProcessing(numberOfItems: Int) {
        total = numberOfItems
        reporter.fraction(if (numberOfItems == 0) 1.0 else 0.0)
    }

    override fun beginItem(item: T & Any) {
        reporter.text(print(item))
    }

    override fun endItem(item: T & Any) {
        reporter.fraction(((++current).toDouble()) / total)
    }
}