package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.psi.ArendFile
import org.arend.psi.ext.impl.ArendGroup
import org.arend.quickfix.AbstractEWCCAnnotator
import org.arend.term.concrete.Concrete
import org.arend.typechecking.DumbTypecheckerState
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.typecheckable.provider.EmptyConcreteProvider
import org.arend.typechecking.visitor.DesugarVisitor
import org.arend.typechecking.visitor.DumbTypechecker

class DumbTypecheckerPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : BaseGroupPass(file, group, editor, "Arend dumb typechecker annotator", textRange, highlightInfoProcessor) {

    private val typecheckerState = DumbTypecheckerState(TypeCheckingService.getInstance(myProject))

    override fun visitDefinition(definition: Concrete.Definition, progress: ProgressIndicator) {
        DesugarVisitor.desugar(definition, file.concreteProvider, this)
        if (typecheckerState.getTypechecked(definition.data) != null) {
            return
        }

        progress.checkCanceled()
        definition.accept(object : DumbTypechecker(this) {
            override fun visitFunction(def: Concrete.FunctionDefinition, params: Void?): Void? {
                super.visitFunction(def, params)
                AbstractEWCCAnnotator.makeAnnotator(def.data.data as? PsiElement)?.doAnnotate(holder)
                return null
            }

            override fun visitClassFieldImpl(classFieldImpl: Concrete.ClassFieldImpl, params: Void?) {
                AbstractEWCCAnnotator.makeAnnotator(classFieldImpl.data as? PsiElement)?.doAnnotate(holder)
                super.visitClassFieldImpl(classFieldImpl, params)
            }

            override fun visitClassExt(expr: Concrete.ClassExtExpression, params: Void?): Void? {
                AbstractEWCCAnnotator.makeAnnotator(expr.data as? PsiElement)?.doAnnotate(holder)
                super.visitClassExt(expr, params)
                return null
            }

            override fun visitNew(expr: Concrete.NewExpression, params: Void?): Void? {
                if (expr.expression !is Concrete.ClassExtExpression) {
                    AbstractEWCCAnnotator.makeAnnotator(expr.data as? PsiElement)?.doAnnotate(holder)
                }
                super.visitNew(expr, params)
                return null
            }
        }, null)
    }

    override fun collectInfo(progress: ProgressIndicator) {
        super.collectInfo(progress)
        file.concreteProvider = EmptyConcreteProvider.INSTANCE
    }
}
