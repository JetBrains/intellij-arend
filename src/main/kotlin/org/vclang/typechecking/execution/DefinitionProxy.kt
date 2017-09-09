package org.vclang.typechecking.execution

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.ui.ConsoleViewContentType

class DefinitionProxy(
    definitionName: String,
    isSuite: Boolean,
    locationUrl: String?,
    preservePresentableName: Boolean = false
) : SMTestProxy(definitionName, isSuite, locationUrl, preservePresentableName) {

    fun addText(text: String, contentType: ConsoleViewContentType) =
        addAfterLastPassed { it.print(text, contentType) }

    fun addHyperlink(text: String, info: HyperlinkInfo?) =
        addAfterLastPassed { it.printHyperlink(text, info) }
}
