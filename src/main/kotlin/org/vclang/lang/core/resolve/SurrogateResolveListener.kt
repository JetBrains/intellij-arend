package org.vclang.lang.core.resolve

import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.error.GeneralError
import com.jetbrains.jetpad.vclang.frontend.resolving.OpenCommand
import com.jetbrains.jetpad.vclang.frontend.resolving.ResolveListener
import com.jetbrains.jetpad.vclang.term.Abstract
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.ext.adapters.ClassViewAdapter
import org.vclang.lang.core.psi.ext.adapters.ClassViewFieldAdapter
import org.vclang.lang.core.psi.ext.adapters.ClassViewInstanceAdapter
import org.vclang.lang.core.psi.ext.adapters.ImplementationAdapter

class SurrogateResolveListener(private val errorReporter: ErrorReporter) : ResolveListener {
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
    ) { (openCmd as Surrogate.NamespaceCommandStatement).resolvedClass = definition }

    override fun implementResolved(
            implementDef: Abstract.Implementation,
            definition: Abstract.ClassField
    ) = (implementDef as ImplementationAdapter).setImplemented(definition)

    override fun implementResolved(
            implementStmt: Abstract.ClassFieldImpl,
            definition: Abstract.ClassField
    ) { (implementStmt as Surrogate.ClassFieldImpl).implementedField = definition }

    override fun classViewResolved(
            classView: Abstract.ClassView,
            classifyingField: Abstract.ClassField
    ) { (classView as ClassViewAdapter).setClassifyingField(classifyingField) }

    override fun classViewFieldResolved(
            classViewField: Abstract.ClassViewField,
            definition: Abstract.ClassField
    ) { (classViewField as ClassViewFieldAdapter).setUnderlyingField(definition) }

    override fun classViewInstanceResolved(
            instance: Abstract.ClassViewInstance,
            classifyingDefinition: Abstract.Definition
    ) { (instance as ClassViewInstanceAdapter).classifyingDefinition = classifyingDefinition }

    override fun makeBinOp(
            binOpExpr: Abstract.BinOpSequenceExpression,
            left: Abstract.Expression,
            binOp: Abstract.Definition,
            variable: Abstract.ReferenceExpression,
            right: Abstract.Expression
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
            patterns: List<Abstract.Pattern>,
            index: Int,
            constructor: Abstract.Constructor
    ) {
        val pattern = patterns[index] as Surrogate.Pattern
        val constructorPattern = Surrogate.ConstructorPattern(
                pattern.position,
                pattern.isExplicit,
                constructor,
                emptyList()
        )
        (patterns as MutableList<Surrogate.Pattern>)[index] = constructorPattern
    }

    override fun replaceWithConstructor(
            clause: Abstract.FunctionClause,
            index: Int,
            constructor: Abstract.Constructor
    ) { (clause as Surrogate.FunctionClause).replaceWithConstructor(index, constructor) }

    override fun patternResolved(
            pattern: Abstract.ConstructorPattern,
            definition: Abstract.Constructor
    ) { (pattern as Surrogate.ConstructorPattern).constructor = definition }

    override fun report(error: GeneralError) =  errorReporter.report(error)
}
