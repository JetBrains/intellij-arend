package org.arend.tracer

import org.arend.core.context.binding.Binding
import org.arend.core.expr.Expression
import org.arend.ext.prettifier.ExpressionPrettifier
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.Doc
import org.arend.ext.prettyprinting.doc.DocFactory
import org.arend.term.concrete.Concrete
import org.arend.typechecking.TypecheckingContext
import org.arend.typechecking.error.local.GoalDataHolder

class ArendTraceSyntheticError(
    prettifier: ExpressionPrettifier?,
    cause: Concrete.Expression,
    typecheckingContext: TypecheckingContext?,
    bindingTypes: MutableMap<Binding, Expression>,
    private val inspectedTermCore: Expression?,
    expectedTypeCore: Expression?
) : GoalDataHolder(
    prettifier, Level.INFO, "", cause,
    typecheckingContext, bindingTypes, expectedTypeCore
) {

    override fun getBodyDoc(ppConfig: PrettyPrinterConfig?): Doc {
        val expectedDoc = getExpectedDoc(ppConfig)
        val inspectedDoc = getInspectedDoc(ppConfig)
        val contextDoc = getContextDoc(ppConfig)
        return DocFactory.vList(expectedDoc, inspectedDoc, contextDoc)
    }

    private fun getInspectedDoc(ppConfig: PrettyPrinterConfig?): Doc {
        return if (inspectedTermCore == null) DocFactory.nullDoc()
        else DocFactory.hang(DocFactory.text("Inspected term:"), inspectedTermCore.prettyPrint(ppConfig))
    }
}