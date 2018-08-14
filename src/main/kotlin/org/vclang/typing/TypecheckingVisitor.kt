package org.vclang.typing

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.naming.reference.*
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.prelude.Prelude
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcDefData
import org.vclang.psi.VcDefFunction
import org.vclang.psi.VcDefinition
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.ext.impl.DefinitionAdapter
import java.math.BigInteger
import java.util.*


class TypecheckingVisitor(private val element: VcCompositeElement, private val holder: AnnotationHolder) : AbstractExpressionVisitor<Any,Unit> {
    override fun visitErrors() = false

    private class GetKindDefVisitor : GetKindVisitor() {
        var def: VcDefinition? = null

        override fun getReferenceKind(ref: Referable): GetKindVisitor.Kind {
            if (ref is VcDefinition) {
                def = ref
            }
            return super.getReferenceKind(ref)
        }

        override fun visitClassExt(data: Any?, isNew: Boolean, baseClass: Abstract.Expression?, implementations: Collection<Abstract.ClassFieldImpl>?, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?): Kind {
            if (!isNew && baseClass != null) {
                baseClass.accept(this, null)
            }
            return if (isNew) Kind.NEW else Kind.CLASS_EXT
        }
    }

    private fun toString(any: Any) = if (any is PsiElement) any.text else any.toString()

    private fun typeMismatch(expectedType: String, actualType: String) {
        holder.createAnnotation(HighlightSeverity.ERROR, element.textRange, "Expected $expectedType; Actual type: $actualType", "Type mismatch<br>Expected $expectedType<br>Actual type: $actualType")
    }

    private fun typeMismatchFull(expectedType: String, actualType: String) {
        holder.createAnnotation(HighlightSeverity.ERROR, element.textRange, "Expected type: $expectedType", "Type mismatch<br>Expected type: $expectedType<br>Actual type: $actualType")
    }

    private fun getKind(type: Any, visitor: GetKindVisitor) =
        when (type) {
            is Abstract.Expression -> type.accept(visitor, null)
            is ExpectedTypeVisitor.Substituted -> {
                val kind = type.expr.accept(visitor, null)
                if (kind === GetKindVisitor.Kind.REFERENCE) GetKindVisitor.Kind.APP else kind
            }
            else -> null
        }

    private fun hasCoerce(def: VcDefinition, fromOther: Boolean): Boolean {
        if (!fromOther && def is VcDefClass) {
            val visited = HashSet<ClassReferable>()
            val toVisit = ArrayDeque<ClassReferable>()
            toVisit.add(def)
            while (!toVisit.isEmpty()) {
                val cur = toVisit.pop()
                if (!visited.add(cur)) {
                    continue
                }
                if (cur is Abstract.ClassDefinition && cur.parameters.any { it.isExplicit }) {
                    return true
                }
                toVisit.addAll(cur.superClassReferences)
            }
        }

        val stats = (def as? DefinitionAdapter<*>)?.getWhere()?.statementList ?: return false
        for (stat in stats) {
            val statDef = stat.definition
            if (statDef is VcDefFunction && statDef.coerceKw != null) {
                return true
            }
        }
        return false
    }

    private fun compare(actualType: Any, expectedType: Any) {
        if ((expectedType is Abstract.Expression || expectedType is ExpectedTypeVisitor.Substituted) && (actualType is Abstract.Expression || actualType is ExpectedTypeVisitor.Substituted)) {
            // TODO: Compare expressions
            return
        }

        val visitor = GetKindDefVisitor()
        val expectedKind = getKind(expectedType, visitor)
        if (expectedKind != null && !expectedKind.isWHNF()) {
            return
        }
        val expectedDef = visitor.def
        val actualKind = getKind(actualType, visitor)
        if (actualKind != null && !actualKind.isWHNF()) {
            return
        }
        val actualDef = visitor.def

        if (expectedKind != null) {
            if (actualType == ExpectedTypeVisitor.Universe && expectedKind != GetKindVisitor.Kind.UNIVERSE && expectedKind.isWHNF() &&
                (expectedKind != GetKindVisitor.Kind.DATA && expectedKind != GetKindVisitor.Kind.CLASS && expectedKind != GetKindVisitor.Kind.CLASS_EXT || expectedDef == null || !hasCoerce(expectedDef, true))) {
                    typeMismatchFull(toString(expectedType), "a universe")
            }
            return
        }

        when (expectedType) {
            ExpectedTypeVisitor.Universe ->
                if (actualKind != GetKindVisitor.Kind.UNIVERSE && actualType !== ExpectedTypeVisitor.Universe &&
                    (actualKind != GetKindVisitor.Kind.DATA && actualKind != GetKindVisitor.Kind.CLASS && actualKind != GetKindVisitor.Kind.CLASS_EXT || actualDef == null || !hasCoerce(actualDef, false))) {
                        typeMismatch("a universe", toString(actualType))
                }
            ExpectedTypeVisitor.Data ->
                if (actualKind !== GetKindVisitor.Kind.DATA) {
                    typeMismatch("a data", toString(actualType))
                }
            is ExpectedTypeVisitor.Definition ->
                if (!(expectedType.def is VcDefData && actualKind === GetKindVisitor.Kind.DATA || expectedKind is VcDefClass && (actualKind === GetKindVisitor.Kind.CLASS || actualKind === GetKindVisitor.Kind.CLASS_EXT))) {
                    typeMismatch(expectedType.def.textRepresentation(), toString(actualType))
                }
            is ExpectedTypeVisitor.Sigma -> {
                if (actualKind == null || actualKind != GetKindVisitor.Kind.SIGMA && actualKind.isWHNF() &&
                    (actualKind != GetKindVisitor.Kind.DATA && actualKind != GetKindVisitor.Kind.CLASS && actualKind != GetKindVisitor.Kind.CLASS_EXT || actualDef == null || !hasCoerce(actualDef, false))) {
                    typeMismatch("a sigma type", toString(actualType))
                }
            }
        }
    }

    private fun checkReference(referent: Referable, expectedType: Any?) {
        if (referent !is TypedReferable) {
            return
        }

        if (expectedType == null) {
            return
        }
        val actualType = referent.typeOf ?: return
        if (actualType is ExpectedTypeVisitor.Error) {
            holder.createErrorAnnotation(element, toString(actualType))
            return
        }

        compare(actualType, expectedType)
    }

    override fun visitReference(data: Any?, referent: Referable, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, expectedType: Any?) {
        if (level1 != null || level2 != null) {
            checkIsGlobal(referent)
        }
        checkReference(referent, expectedType)
    }

    override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, errorData: Abstract.ErrorData?, expectedType: Any?) {
        checkIsGlobal(referent)
        checkReference(referent, expectedType)
    }

    private fun checkIsGlobal(referent: Referable) {
        val ref = ExpressionResolveNameVisitor.resolve(referent, element.scope)
        if (ref !is ErrorReference && ref !is GlobalReferable) {
            holder.createErrorAnnotation(element, "Levels are allowed only after definitions")
        }
    }

    override fun visitLam(data: Any?, parameters: Collection<Abstract.Parameter>, body: Abstract.Expression?, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitUniverse(data: Any?, pLevelNum: Int?, hLevelNum: Int?, pLevel: Abstract.LevelExpression?, hLevel: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitInferHole(data: Any?, errorData: Abstract.ErrorData?, expectedType: Any?) {}

    override fun visitGoal(data: Any?, name: String?, expression: Abstract.Expression?, errorData: Abstract.ErrorData?, expectedType: Any?) {
        val expectedTypeString = if (expectedType == null) "" else "Expected type: " + (if (expectedType is PsiElement) expectedType.text else toString(expectedType))
        holder.createAnnotation(HighlightSeverity.WARNING, element.textRange, if (expectedTypeString != "") expectedTypeString else "Goal", "Goal" + if (expectedTypeString != "") "<br>$expectedTypeString" else "")
    }

    override fun visitTuple(data: Any?, fields: Collection<Abstract.Expression>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitSigma(data: Any?, parameters: Collection<Abstract.Parameter>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitCase(data: Any?, expressions: Collection<Abstract.Expression>, clauses: Collection<Abstract.FunctionClause>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Try to infer the type of expressions and check that constructors in clauses have correct types.
        //       If cannot infer the type, check that constructors have the same type.
    }

    override fun visitFieldAccs(data: Any?, expression: Abstract.Expression, fieldAccs: Collection<Int>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitClassExt(data: Any?, isNew: Boolean, baseClass: Abstract.Expression?, implementations: Collection<Abstract.ClassFieldImpl>?, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitLet(data: Any?, clauses: Collection<Abstract.LetClause>, expression: Abstract.Expression?, errorData: Abstract.ErrorData?, expectedType: Any?) {}

    override fun visitNumericLiteral(data: Any?, number: BigInteger, errorData: Abstract.ErrorData?, expectedType: Any?) {
        if (expectedType == null || expectedType == ExpectedTypeVisitor.Data) {
            return
        }
        var kind: GetKindVisitor.Kind? = null
        val def = if (expectedType is ExpectedTypeVisitor.Definition) {
            expectedType.def
        } else {
            val expr = when (expectedType) {
                is Abstract.Expression -> expectedType
                is ExpectedTypeVisitor.Substituted -> expectedType.expr
                else -> null
            }
            if (expr != null) {
                val visitor = GetKindDefVisitor()
                kind = expr.accept(visitor, null)
                if (kind === GetKindVisitor.Kind.REFERENCE && expectedType is ExpectedTypeVisitor.Substituted) {
                    kind = GetKindVisitor.Kind.APP
                }
                visitor.def
            } else {
                null
            }
        }

        val ok = if (def == null) {
            kind != null && !kind.isWHNF()
        } else {
            def == PsiLocatedReferable.fromReferable(Prelude.INT.referable) || number >= BigInteger.ZERO && def == PsiLocatedReferable.fromReferable(Prelude.NAT.referable)
        }

        if (!ok) {
            typeMismatchFull(toString(expectedType), if (number >= BigInteger.ZERO) Prelude.NAT.name else Prelude.INT.name)
        }
    }
}