package org.vclang.lang.core.resolve

import com.jetbrains.jetpad.vclang.frontend.resolving.OpenCommand
import com.jetbrains.jetpad.vclang.frontend.resolving.ResolveListener
import com.jetbrains.jetpad.vclang.term.Abstract
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.ext.adapters.ClassImplementAdapter
import org.vclang.lang.core.psi.ext.adapters.ClassViewAdapter
import org.vclang.lang.core.psi.ext.adapters.ClassViewFieldAdapter
import org.vclang.lang.core.psi.ext.adapters.ClassViewInstanceAdapter

class SurrogateResolveListener : ResolveListener {

    override fun nameResolved(
            referenceExpression: Abstract.ReferenceExpression,
            referable: Abstract.ReferableSourceNode
    ) = (referenceExpression as Surrogate.ReferenceExpression).setResolvedReferent(referable)

    override fun moduleResolved(
            moduleCallExpression: Abstract.ModuleCallExpression,
            module: Abstract.Definition
    ) {
        (moduleCallExpression as Surrogate.ModuleCallExpression).module = module
    }

    override fun openCmdResolved(
            openCmd: OpenCommand,
            definition: Abstract.Definition
    ) {
        (openCmd as Surrogate.NamespaceCommandStatement).resolvedClass = definition
    }

    override fun implementResolved(
            implementDef: Abstract.Implementation,
            definition: Abstract.ClassField
    ) = (implementDef as ClassImplementAdapter).setImplemented(definition)

    override fun implementResolved(
            implementStmt: Abstract.ClassFieldImpl,
            definition: Abstract.ClassField
    ) {
        (implementStmt as Surrogate.ClassFieldImpl).implementedField = definition
    }

    override fun classViewResolved(
            classView: Abstract.ClassView,
            classifyingField: Abstract.ClassField
    ) {
        (classView as ClassViewAdapter).setClassifyingField(classifyingField)
    }

    override fun classViewFieldResolved(
            classViewField: Abstract.ClassViewField,
            definition: Abstract.ClassField
    ) {
        (classViewField as ClassViewFieldAdapter).setUnderlyingField(definition)
    }

    override fun classViewInstanceResolved(
            instance: Abstract.ClassViewInstance,
            classifyingDefinition: Abstract.Definition
    ) {
        (instance as ClassViewInstanceAdapter).classifyingDefinition = classifyingDefinition
    }

    override fun makeBinOp(
            binOpExpr: Abstract.BinOpSequenceExpression,
            left: Abstract.Expression,
            binOp: Abstract.ReferableSourceNode,
            variable: Abstract.ReferenceExpression,
            right: Abstract.Expression?
    ): Abstract.BinOpExpression =
            (binOpExpr as Surrogate.BinOpSequenceExpression).makeBinOp(left, binOp, variable, right)

    override fun makeError(
            binOpExpr: Abstract.BinOpSequenceExpression,
            node: Abstract.SourceNode
    ): Abstract.Expression = (binOpExpr as Surrogate.BinOpSequenceExpression).makeError(node)

    override fun replaceBinOp(
            binOpExpr: Abstract.BinOpSequenceExpression,
            expression: Abstract.Expression
    ) = (binOpExpr as Surrogate.BinOpSequenceExpression).replace(expression)

    override fun replaceWithConstructor(
            container: Abstract.PatternContainer,
            index: Int,
            constructor: Abstract.Constructor
    ) {
        val patternContainer = container as Surrogate.PatternContainer
        val old = patternContainer.patterns!![index]
        val newPattern = Surrogate.ConstructorPattern(
                old.position,
                old.isExplicit,
                constructor,
                emptyList()
        )
        patternContainer.patterns!![index] = newPattern
    }

    override fun patternResolved(
            pattern: Abstract.ConstructorPattern,
            definition: Abstract.Constructor
    ) {
        (pattern as Surrogate.ConstructorPattern).constructor = definition
    }
}
