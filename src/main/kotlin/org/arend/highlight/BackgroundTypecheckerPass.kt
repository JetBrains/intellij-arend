package org.arend.highlight

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.codeInsight.daemon.impl.LineMarkersPass
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.core.context.binding.Binding
import org.arend.core.context.binding.Variable
import org.arend.core.definition.ClassDefinition
import org.arend.core.definition.ClassField
import org.arend.core.definition.Definition
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCReferable
import org.arend.psi.ArendClassField
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.psi.ext.impl.ArendGroup
import org.arend.quickfix.implementCoClause.doAnnotate
import org.arend.settings.ArendSettings
import org.arend.term.ClassFieldKind
import org.arend.term.concrete.Concrete
import org.arend.term.group.Group
import org.arend.typechecking.*
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.provider.EmptyConcreteProvider
import org.arend.typechecking.visitor.DesugarVisitor
import org.arend.typechecking.visitor.DumbTypechecker
import org.arend.util.FullName

class BackgroundTypecheckerPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : BaseGroupPass(file, group, editor, "Arend background typechecker annotator", textRange, highlightInfoProcessor), TypecheckingListener {

    private val typeCheckingService = myProject.service<TypeCheckingService>()
    private val definitionBlackListService = service<DefinitionBlacklistService>()
    private val arendSettings = service<ArendSettings>()
    private val definitionsToTypecheck = LinkedHashSet<ArendDefinition>()
    private var lineMarkersPass: LineMarkersPass? = null
    private var currentDefinition: ArendDefinition? = null

    override fun referableTypechecked(referable: Referable, binding: Binding) {
        ArendTypecheckingListener.referableTypechecked(referable, binding)
    }

    override fun referenceTypechecked(referable: Referable, variable: Variable) {
        if (!(variable is ClassField && variable.isProperty && currentDefinition?.let { definitionsToTypecheck.contains(it) } == true)) {
            return
        }
        val psi = referable.underlyingReferable as? ArendClassField ?: return
        if (psi.classFieldKind != ClassFieldKind.PROPERTY) {
            holder.createInfoAnnotation(psi.defIdentifier, null).textAttributes = ArendHighlightingColors.PROPERTY.textAttributesKey
        }
    }

    override fun typecheckingHeaderStarted(definition: TCReferable) {
        currentDefinition = definition.data as? ArendDefinition
    }

    override fun typecheckingBodyStarted(definition: TCReferable) {
        currentDefinition = definition.data as? ArendDefinition
    }

    override fun typecheckingUnitStarted(definition: TCReferable) {
        currentDefinition = definition.data as? ArendDefinition
    }

    override fun typecheckingHeaderFinished(referable: TCReferable, definition: Definition) {
        currentDefinition = null
    }

    override fun typecheckingBodyFinished(referable: TCReferable, definition: Definition) {
        currentDefinition = null
    }

    override fun typecheckingUnitFinished(referable: TCReferable, definition: Definition) {
        currentDefinition = null
    }

    override fun typecheckingInterrupted(definition: TCReferable, typechecked: Definition?) {
        currentDefinition = null
    }

    override fun visitDefinition(definition: Concrete.Definition, progress: ProgressIndicator) {
        DesugarVisitor.desugar(definition, file.concreteProvider, this)

        progress.checkCanceled()
        definition.accept(object : DumbTypechecker(this) {
            override fun visitFunction(def: Concrete.FunctionDefinition, params: Void?): Void? {
                super.visitFunction(def, params)
                doAnnotate(def.data.data as? PsiElement, holder)
                return null
            }

            override fun visitClassFieldImpl(classFieldImpl: Concrete.ClassFieldImpl, params: Void?) {
                doAnnotate(classFieldImpl.data as? PsiElement, holder)
                super.visitClassFieldImpl(classFieldImpl, params)
            }

            override fun visitClassExt(expr: Concrete.ClassExtExpression, params: Void?): Void? {
                doAnnotate(expr.data as? PsiElement, holder)
                super.visitClassExt(expr, params)
                return null
            }

            override fun visitNew(expr: Concrete.NewExpression, params: Void?): Void? {
                if (expr.expression !is Concrete.ClassExtExpression)
                    doAnnotate(expr.data as? PsiElement, holder)
                super.visitNew(expr, params)
                return null
            }
        }, null)
    }

    private fun typecheckDefinition(typechecking: ArendTypechecking, definition: ArendDefinition, progress: ProgressIndicator): Concrete.Definition? {
        val result = (typechecking.concreteProvider.getConcrete(definition) as? Concrete.Definition)?.let {
            val ok = definitionBlackListService.runTimed(definition, progress) {
                typechecking.typecheckDefinitions(listOf(it), ArendCancellationIndicator(progress))
            }

            if (!ok) {
                NotificationErrorReporter(myProject).warn("Typechecking of ${FullName(it.data)} was interrupted after ${arendSettings.typecheckingTimeLimit} second(s)")
                if (definitionsToTypecheck.last() != definition) {
                    DaemonCodeAnalyzer.getInstance(myProject).restart(file)
                }
            }

            it
        }

        if (result is Concrete.ClassDefinition) {
            for (field in (typeCheckingService.typecheckerState.getTypechecked(result.data) as? ClassDefinition)?.personalFields ?: emptyList()) {
                if (field.isProperty) {
                    val psiField = field.referable.underlyingReferable as? ArendClassField
                    if (psiField != null && psiField.classFieldKind != ClassFieldKind.PROPERTY) {
                        holder.createInfoAnnotation(psiField.defIdentifier, null).textAttributes = ArendHighlightingColors.DECLARATION_PROP.textAttributesKey
                    }
                }
            }
        }

        advanceProgress(1)
        return result
    }

    override fun collectInfo(progress: ProgressIndicator) {
        when (arendSettings.typecheckingMode) {
            ArendSettings.TypecheckingMode.SMART -> if (definitionsToTypecheck.isNotEmpty()) {
                val typechecking = ArendTypechecking.create(myProject, BackgroundTypecheckerState(typeCheckingService.typecheckerState), this)
                val lastModified = file.lastModifiedDefinition
                if (lastModified != null) {
                    val typechecked = if (definitionsToTypecheck.remove(lastModified)) {
                        typecheckDefinition(typechecking, lastModified, progress)?.let { typeCheckingService.typecheckerState.getTypechecked(it.data) }
                    } else null
                    if (typechecked?.status()?.withoutErrors() == true) {
                        file.lastModifiedDefinition = null
                        for (definition in definitionsToTypecheck) {
                            typecheckDefinition(typechecking, definition, progress)
                        }
                    } else {
                        for (definition in definitionsToTypecheck) {
                            visitDefinition(definition, progress)
                        }
                    }
                } else {
                    for (definition in definitionsToTypecheck) {
                        typecheckDefinition(typechecking, definition, progress)
                    }
                }

                val constructor = LineMarkersPass::class.java.declaredConstructors[0]
                constructor.isAccessible = true
                val pass = constructor.newInstance(file.project, file, document, textRange, textRange) as LineMarkersPass
                pass.id = Pass.LINE_MARKERS
                pass.collectInformation(progress)
                lineMarkersPass = pass
            }
            ArendSettings.TypecheckingMode.DUMB ->
                for (definition in definitionsToTypecheck) {
                    visitDefinition(definition, progress)
                }
            ArendSettings.TypecheckingMode.OFF -> {}
        }

        file.concreteProvider = EmptyConcreteProvider.INSTANCE
    }

    override fun applyInformationWithProgress() {
        super.applyInformationWithProgress()
        lineMarkersPass?.applyInformationToEditor()
    }

    override fun countDefinition(def: ArendDefinition) =
        if (!definitionBlackListService.isBlacklisted(def) && typeCheckingService.getTypechecked(def) == null) {
            definitionsToTypecheck.add(def)
            true
        } else false

    override fun numberOfDefinitions(group: Group) =
        if (arendSettings.typecheckingMode == ArendSettings.TypecheckingMode.OFF) 0 else super.numberOfDefinitions(group)
}
