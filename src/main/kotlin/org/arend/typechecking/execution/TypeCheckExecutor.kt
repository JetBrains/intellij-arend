package org.arend.typechecking.execution

import com.intellij.execution.executors.DefaultRunExecutor
import org.arend.util.ArendBundle

class TypeCheckExecutor : DefaultRunExecutor() {

    override fun getDescription(): String = ArendBundle.message("arend.line.marker.description")

    override fun getActionName(): String = ArendBundle.message("arend.line.marker.action.name")

    override fun getStartActionText(): String = ArendBundle.message("arend.line.marker.action.text")

    override fun getStartActionText(configurationName: String): String = configurationName
}
