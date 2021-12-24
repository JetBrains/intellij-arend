package org.arend.quickfix.instance

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.binding.TypedBinding
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.ClassDefinition
import org.arend.core.expr.ClassCallExpression
import org.arend.ext.prettyprinting.DefinitionRenamer
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.variable.VariableImpl
import org.arend.psi.*
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.*
import org.arend.resolving.DataLocatedReferable
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.typechecking.error.local.inference.InstanceInferenceError
import org.arend.util.ArendBundle
import java.lang.Integer.max

class AddInstanceArgumentQuickFix(val error: InstanceInferenceError, val cause: SmartPsiElementPointer<ArendLongName>):
    IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.instance.addLocalInstance")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        ((cause.element as? PsiElement)?.ancestor<ArendDefinition>()?.let{ it is ArendFunctionalDefinition || it is ArendDefData }?: false) &&
                (error.classRef.data as? SmartPsiElementPointer<*>)?.element != null && ((error.classRef.typechecked as? ClassDefinition)?.classifyingField == null || error.classifyingExpression != null)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val ambientDefinition = (cause.element as? PsiElement)?.ancestor<ArendDefinition>()
        val missingClassInstance = (error.classRef.data as? SmartPsiElementPointer<*>)?.element
        val implementedClass = error.classRef.typechecked
        if (ambientDefinition is PsiConcreteReferable && missingClassInstance is ArendDefClass && implementedClass is ClassDefinition) {
            val psiFactory = ArendPsiFactory(project)
            val className = ResolveReferenceAction.getTargetName(missingClassInstance, ambientDefinition).let { if (it.isNullOrEmpty()) missingClassInstance.defIdentifier?.textRepresentation() else it }
            val ppConfig = object : PrettyPrinterConfig { override fun getDefinitionRenamer(): DefinitionRenamer = PsiLocatedRenamer(ambientDefinition) }
            val classifyingTypeExpr = this.error.classifyingExpression?.let{ ToAbstractVisitor.convert(it, ppConfig) }
            val classifyingField = implementedClass.classifyingField
            val fCallOk = classifyingField == null || implementedClass.fields.filter { it.referable.isExplicitField && !implementedClass.isImplemented(it) }.let{ it.isNotEmpty() && it[0] == classifyingField }
            val classCall = if (fCallOk) className + (classifyingTypeExpr?.let { if (argNeedsParentheses(it)) " ($it)" else " $it" } ?: "") else "$className { | ${classifyingField!!.name} => $classifyingTypeExpr }"
            val ambientDefTypechecked = (error.definition as DataLocatedReferable).typechecked
            val l  = when (ambientDefinition) {
                is ArendFunctionalDefinition -> ambientDefinition.nameTeleList.map { tele -> Pair(tele, tele.identifierOrUnknownList.size) }
                is ArendDefData -> ambientDefinition.typeTeleList.map { tele -> Pair(tele, tele.typedExpr?.identifierOrUnknownList?.size ?: 1) }
                else -> null
            }
            var anchor : PsiElement? = ambientDefinition.nameIdentifier
            if (ambientDefTypechecked != null && l != null) {
                val parameters = DependentLink.Helper.toList(ambientDefTypechecked.parameters)
                var parameterIndex = this.error.classifyingExpression?.findFreeBindings()?.map { parameters.indexOf(it) }?.fold(-1) { acc, p -> max(acc, p) } ?: -1
                val iterator = l.iterator()
                do {
                    if (parameterIndex >= 0) {
                        val tele = iterator.next()
                        parameterIndex -= tele.second
                        anchor = tele.first
                    }
                } while (iterator.hasNext() && parameterIndex >= 0)
            }

            val sampleVar = (error.classRef.typechecked as? ClassDefinition)?.let{ TypedBinding(null, ClassCallExpression(it, it.makeIdLevels())) } ?: VariableImpl("_")
            addImplicitClassDependency(psiFactory, ambientDefinition, classCall, sampleVar, anchor = anchor)
        }
    }

}