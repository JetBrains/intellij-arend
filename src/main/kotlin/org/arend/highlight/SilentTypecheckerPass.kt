package org.arend.highlight

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.editor.ArendOptions
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.psi.ext.impl.ArendGroup
import org.arend.quickfix.AbstractEWCCAnnotator
import org.arend.term.concrete.Concrete
import org.arend.term.group.Group
import org.arend.typechecking.SilentTypechecking
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.typecheckable.provider.EmptyConcreteProvider
import org.arend.typechecking.visitor.DesugarVisitor
import org.arend.typechecking.visitor.DumbTypechecker

class SilentTypecheckerPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : BaseGroupPass(file, group, editor, "Arend silent typechecker annotator", textRange, highlightInfoProcessor) {

    private val typeCheckingService = TypeCheckingService.getInstance(myProject)
    private val definitionsToTypecheck = ArrayList<ArendDefinition>()

    override fun visitDefinition(definition: Concrete.Definition, progress: ProgressIndicator) {
        DesugarVisitor.desugar(definition, file.concreteProvider, this)

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
        when (ArendOptions.instance.typecheckingMode) {
            ArendOptions.TypecheckingMode.SMART -> if (definitionsToTypecheck.isNotEmpty()) {
                val typechecking = SilentTypechecking(myProject, typeCheckingService)
                for (definition in definitionsToTypecheck) {
                    (typechecking.concreteProvider.getConcrete(definition) as? Concrete.Definition)?.let {
                        typechecking.typecheckDefinitions(listOf(it)) {
                            progress.isCanceled
                        }
                    }
                    advanceProgress(1)
                }
            }
            ArendOptions.TypecheckingMode.DUMB ->
                for (definition in definitionsToTypecheck) {
                    visitDefinition(definition, progress)
                }
            ArendOptions.TypecheckingMode.OFF -> {}
        }

        file.concreteProvider = EmptyConcreteProvider.INSTANCE
    }

    override fun applyInformationWithProgress() {
        if (ArendOptions.instance.typecheckingMode == ArendOptions.TypecheckingMode.SMART) {
            DaemonCodeAnalyzer.getInstance(myProject).restart(file) // To update line markers
        } else {
            super.applyInformationWithProgress()
        }
    }

    override fun countDefinition(def: ArendDefinition) =
        if (typeCheckingService.getTypechecked(def) == null) {
            definitionsToTypecheck.add(def)
            true
        } else false

    override fun numberOfDefinitions(group: Group): Int =
        if (ArendOptions.instance.typecheckingMode == ArendOptions.TypecheckingMode.OFF) 0 else super.numberOfDefinitions(group)
}
