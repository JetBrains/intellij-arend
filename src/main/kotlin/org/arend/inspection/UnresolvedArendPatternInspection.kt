package org.arend.inspection

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.childrenOfType
import org.arend.psi.ArendFile
import org.arend.psi.ArendFileScope
import org.arend.psi.ancestor
import org.arend.psi.ext.*
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.refactoring.isPrelude
import org.arend.term.abs.Abstract
import org.arend.term.group.Group
import org.arend.util.ArendBundle

class UnresolvedArendPatternInspection : ArendInspectionBase() {
    override fun buildArendVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {

            fun registerProblem(element: PsiElement) {
                val message = ArendBundle.message("arend.inspection.unresolved.pattern", element.text)
                holder.registerProblem(element, message)
            }

            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                if (element !is ArendPattern) {
                    return
                }

                val identifier = element.singleReferable ?: return
                val name = identifier.refName
                val project = element.project
                val constructors = StubIndex.getElements(ArendDefinitionIndex.KEY, name, project, ArendFileScope(project), PsiReferable::class.java).filterIsInstance<ArendConstructor>()
                val resolve = identifier.reference?.resolve() ?: return

                if (constructors.isNotEmpty() && (!constructors.contains(resolve) &&
                    (resolve.containingFile as? ArendFile?)?.let { isPrelude(it) } == false)) {
                    val group = (getTypeOfPattern(element) as? ArendLiteral?)?.refIdentifier?.resolve as? Group?
                    val groupConstructors = group?.constructors ?: emptyList()
                    if (groupConstructors.any { constructors.contains(it) }) {
                        registerProblem(element)
                    }
                }
            }

            private fun getTypeOfPattern(pattern: ArendPattern): PsiElement? {
                val patterns = pattern.parent?.childrenOfType<ArendPattern>()
                val firstPattern = patterns?.first()
                val firstPatternResolve = firstPattern?.singleReferable?.reference?.resolve()
                val index = patterns?.indexOf(pattern) ?: -1
                return if (firstPatternResolve is Abstract.ParametersHolder) {
                    val parameter = firstPatternResolve.parameters[index - 1]
                    parameter?.type as? PsiElement
                } else if (pattern.parent is ArendPattern) {
                    val parentGroup = getTypeOfPattern(pattern.parent as ArendPattern) as? Abstract.ParametersHolder?
                    parentGroup?.parameters?.get(index)?.type as? PsiElement?
                } else if (pattern.ancestor<ArendCaseExpr>() != null) {
                    val caseArg = pattern.ancestor<ArendCaseExpr>()?.caseArguments?.get(index)
                    val resolve = (caseArg?.expression as? ArendNewExpr?)?.argumentAppExpr?.atomFieldsAcc?.atom?.literal?.refIdentifier?.resolve
                        ?: caseArg?.eliminatedReference?.resolve
                    (resolve?.ancestor<ArendNameTele>()?.type as? ArendNewExpr?)?.argumentAppExpr?.atomFieldsAcc?.atom?.literal
                } else {
                    null
                }
            }
        }
    }
}
