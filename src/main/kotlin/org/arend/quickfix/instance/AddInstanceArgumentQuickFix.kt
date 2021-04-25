package org.arend.quickfix.instance

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.binding.TypedBinding
import org.arend.core.definition.ClassDefinition
import org.arend.core.expr.ClassCallExpression
import org.arend.core.sort.Sort
import org.arend.ext.prettyprinting.DefinitionRenamer
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.variable.VariableImpl
import org.arend.psi.*
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.PsiLocatedRenamer
import org.arend.refactoring.addImplicitClassDependency
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.typechecking.error.local.inference.InstanceInferenceError

class AddInstanceArgumentQuickFix(val error: InstanceInferenceError, val cause: SmartPsiElementPointer<ArendLongName>):
    IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = "Make enclosing definition depend on the class"

    override fun getFamilyName(): String = "arend.instance"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        ((cause.element as? PsiElement)?.ancestor<ArendDefinition>()?.let{ it is ArendFunctionalDefinition || it is ArendDefData }?: false) &&
                (error.classRef.data as? SmartPsiElementPointer<*>)?.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val ambientDefinition = (cause.element as? PsiElement)?.ancestor<ArendDefinition>()
        val missingClassInstance = (error.classRef.data as? SmartPsiElementPointer<*>)?.element
        if (ambientDefinition is PsiConcreteReferable && missingClassInstance is ArendDefClass) {
            val psiFactory = ArendPsiFactory(project)
            val className = ResolveReferenceAction.getTargetName(missingClassInstance, ambientDefinition).let { if (it.isNullOrEmpty()) missingClassInstance.defIdentifier?.textRepresentation() else it }
            val ppConfig = object : PrettyPrinterConfig { override fun getDefinitionRenamer(): DefinitionRenamer = PsiLocatedRenamer(ambientDefinition) }
            val classifyingTypeExpr = this.error.classifyingExpression?.let{ " " + ToAbstractVisitor.convert(it, ppConfig)?.toString() } ?: ""
            val sampleVar = (error.classRef.typechecked as? ClassDefinition)?.let{ TypedBinding(null, ClassCallExpression(it, Sort.STD)) } ?: VariableImpl("_")
            addImplicitClassDependency(psiFactory, ambientDefinition, className + classifyingTypeExpr, sampleVar)
        }
    }

}