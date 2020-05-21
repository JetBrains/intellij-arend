package org.arend.ui.impl.query

import com.intellij.ui.JBIntSpinner
import org.arend.ext.ui.ArendQuery

class SpinnerQuery(defaultValue: Int) : ArendQuery<Int> {
    val spinner = JBIntSpinner(defaultValue, Int.MIN_VALUE, Int.MAX_VALUE)

    override fun getResult() = spinner.number
}