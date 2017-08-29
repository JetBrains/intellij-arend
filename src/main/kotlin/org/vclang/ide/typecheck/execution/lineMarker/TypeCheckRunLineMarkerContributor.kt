package org.vclang.ide.typecheck.execution.lineMarker

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.vclang.lang.core.parser.fullyQualifiedName
import org.vclang.lang.core.parser.isTypeCheckable
import org.vclang.lang.core.psi.VcDefinition
import org.vclang.lang.core.psi.VcIdentifier

class TypeCheckRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        val parent = element.parent
        if (element is VcIdentifier && parent is VcDefinition && parent.isTypeCheckable) {
            return Info(
                    AllIcons.RunConfigurations.TestState.Run,
                    Function<PsiElement, String> { "Type check ${parent.fullyQualifiedName}" },
                    *ExecutorAction.getActions(1)
            )
        }
        return null
    }
}
