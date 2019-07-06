package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.Definition
import org.arend.core.expr.Expression
import org.arend.core.sort.Sort
import org.arend.naming.reference.TCClassReferable
import org.arend.naming.reference.TCReferable
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.psi.ext.impl.ArendGroup
import org.arend.term.concrete.Concrete
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.TypecheckerState
import org.arend.typechecking.typecheckable.provider.ConcreteProvider
import org.arend.typechecking.visitor.DesugarVisitor
import org.arend.typechecking.visitor.DumbTypechecker

class DumbTypecheckerPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : BaseGroupPass(file, group, editor, "Arend dumb typechecker annotator", textRange, highlightInfoProcessor) {

    private class DumbTypecheckerState(private val service: TypeCheckingService) : TypecheckerState {
        private val map = HashMap<TCReferable, Definition>()

        override fun getTypechecked(ref: TCReferable): Definition? {
            val def = map.computeIfAbsent(ref) {
                (ref.data as? ArendDefinition)?.let { service.getTypechecked(it) } ?: NullDefinition
            }
            return if (def === NullDefinition) null else def
        }

        private object NullDefinition : Definition(TCClassReferable.NULL_REFERABLE, TypeCheckingStatus.NO_ERRORS) {
            override fun getTypeWithParams(params: MutableList<in DependentLink>?, sortArgument: Sort?) = null
            override fun getDefCall(sortArgument: Sort?, args: MutableList<Expression>?) = null
        }

        override fun record(ref: TCReferable, def: Definition?) = getTypechecked(ref)

        override fun rewrite(ref: TCReferable?, def: Definition?) {}

        override fun reset(ref: TCReferable) = getTypechecked(ref)

        override fun reset() {}
    }

    private val typecheckerState = DumbTypecheckerState(TypeCheckingService.getInstance(myProject))

    override fun visitDefinition(definition: Concrete.Definition, concreteProvider: ConcreteProvider, progress: ProgressIndicator) {
        if (typecheckerState.getTypechecked(definition.data) != null) {
            return
        }

        DesugarVisitor.desugar(definition, concreteProvider, this)
        progress.checkCanceled()
        definition.accept(DumbTypechecker(typecheckerState, this), null)
    }
}
