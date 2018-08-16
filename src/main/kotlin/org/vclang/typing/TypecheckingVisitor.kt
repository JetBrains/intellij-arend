package org.vclang.typing

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.naming.reference.ErrorReference
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.reference.TypedReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.prelude.Prelude
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcDefData
import org.vclang.psi.VcLongName
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.typing.ExpectedTypeVisitor.Companion.hasCoerce
import java.math.BigInteger


class TypecheckingVisitor(private val element: VcCompositeElement, private val holder: AnnotationHolder) : AbstractExpressionVisitor<Any,Unit> {
    companion object {
        fun resolveReference(data: Any?, referent: Referable) =
            if (data is VcCompositeElement) {
                if (data !is VcLongName || data.refIdentifierList.size <= 1) {
                    Concrete.ReferenceExpression(data, data.reference?.resolve() as? Referable ?: ErrorReference(data, null, referent.textRepresentation()), Concrete.PLevelExpression(data), Concrete.HLevelExpression(data))
                } else {
                    val refExpr = Concrete.ReferenceExpression(data, referent, Concrete.PLevelExpression(data), Concrete.HLevelExpression(data))
                    val arg = ExpressionResolveNameVisitor.resolve(refExpr, data.scope)
                    (refExpr.referent as? GlobalReferable)?.let {
                        val psiRef = PsiLocatedReferable.fromReferable(it)
                        if (psiRef != null) {
                            refExpr.referent = psiRef
                        }
                    }
                    if (arg == null) refExpr else Concrete.AppExpression.make(data, refExpr, arg, false)
                }
            } else {
                null
            }
    }

    override fun visitErrors() = false

    private fun toString(any: Any) = if (any is PsiElement) any.text else any.toString()

    private fun typeMismatch(expectedType: String, actualType: String) {
        holder.createAnnotation(HighlightSeverity.ERROR, element.textRange, "Expected $expectedType; Actual type: $actualType", "Type mismatch<br>Expected $expectedType<br>Actual type: $actualType")
    }

    private fun typeMismatchFull(expectedType: String, actualType: String) {
        holder.createAnnotation(HighlightSeverity.ERROR, element.textRange, "Expected type: $expectedType", "Type mismatch<br>Expected type: $expectedType<br>Actual type: $actualType")
    }

    private fun getKind(type: Any?, visitor: GetKindVisitor) = when (type) {
        is Abstract.Expression -> type.accept(visitor, null)
        is ExpectedTypeVisitor.Substituted -> {
            val kind = type.expr.accept(visitor, null)
            if (kind === GetKindVisitor.Kind.REFERENCE) GetKindVisitor.Kind.APP else kind
        }
        else -> null
    }

    private fun compareExpr(expr1: Abstract.Expression, isSubst1: Boolean, expr2: Abstract.Expression, isSubst2: Boolean, isLess: Boolean) {
        // TODO: Compare expressions
    }

    private fun compare(actualType: Abstract.Expression, expectedType: Any) {
        val expr = expectedType as? Abstract.Expression ?: (expectedType as? ExpectedTypeVisitor.Substituted)?.expr
        if (expr != null) {
            compareExpr(actualType, false, expr, expectedType is ExpectedTypeVisitor.Substituted, true)
            return
        }

        val visitor = ExpectedTypeVisitor.GetKindDefVisitor()
        val expectedKind = getKind(expectedType, visitor)
        if (expectedKind != null && !expectedKind.isWHNF()) {
            return
        }
        val expectedDef = visitor.def
        val actualKind = actualType.accept(visitor, null)
        if (!actualKind.isWHNF()) {
            return
        }
        val actualDef = visitor.def

        if (expectedKind != null) {
            if (actualType == ExpectedTypeVisitor.Universe && expectedKind != GetKindVisitor.Kind.UNIVERSE && expectedKind.isWHNF() &&
                (expectedKind != GetKindVisitor.Kind.DATA && expectedKind != GetKindVisitor.Kind.CLASS && expectedKind != GetKindVisitor.Kind.CLASS_EXT || expectedDef == null || !hasCoerce(expectedDef, true, ExpectedTypeVisitor.CoerceType.UNIVERSE))) {
                    typeMismatchFull(toString(expectedType), "a universe")
            }
            return
        }

        when (expectedType) {
            ExpectedTypeVisitor.Universe ->
                if (actualKind != GetKindVisitor.Kind.UNIVERSE && actualType != ExpectedTypeVisitor.Universe &&
                    (actualKind != GetKindVisitor.Kind.DATA && actualKind != GetKindVisitor.Kind.CLASS && actualKind != GetKindVisitor.Kind.CLASS_EXT || actualDef == null || !hasCoerce(actualDef, false, ExpectedTypeVisitor.CoerceType.UNIVERSE))) {
                        typeMismatch("a universe", toString(actualType))
                }
            ExpectedTypeVisitor.Data ->
                if (actualKind != GetKindVisitor.Kind.DATA) {
                    typeMismatch("a data", toString(actualType))
                }
            is ExpectedTypeVisitor.Definition ->
                if (!(expectedType.def is VcDefData && actualDef == expectedType.def || expectedType.def is VcDefClass && actualDef is VcDefClass && actualDef.isSubClassOf(expectedType.def))) {
                    typeMismatch(expectedType.def.textRepresentation(), toString(actualType))
                }
            is ExpectedTypeVisitor.Sigma -> {
                if (actualKind != GetKindVisitor.Kind.SIGMA && actualKind.isWHNF() &&
                    (actualKind != GetKindVisitor.Kind.DATA && actualKind != GetKindVisitor.Kind.CLASS && actualKind != GetKindVisitor.Kind.CLASS_EXT || actualDef == null || !hasCoerce(actualDef, false, ExpectedTypeVisitor.CoerceType.SIGMA))) {
                    typeMismatch("a sigma type", toString(actualType))
                }
            }
        }
    }

    private fun checkReference(data: Any?, referent: Referable, expectedType: Any?, withLevels: Boolean) {
        if (expectedType == null && !withLevels) {
            return
        }

        val expr = resolveReference(data, referent) ?: return
        val ref = (expr as? Concrete.ReferenceExpression ?: ((expr as? Concrete.AppExpression)?.function as? Concrete.ReferenceExpression))?.referent as? TypedReferable ?: return
        if (withLevels && ref !is ErrorReference && ref !is GlobalReferable) {
            holder.createErrorAnnotation(element, "Levels are allowed only after definitions")
        }
        if (expectedType == null) {
            return
        }
        val actualType = ref.typeOf ?: return

        if (expr is Concrete.AppExpression) {
            // TODO: drop one parameter in actualType
            return
        }

        compare(actualType, expectedType)
    }

    override fun visitReference(data: Any?, referent: Referable, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, expectedType: Any?) =
        checkReference(data, referent, expectedType, level1 != null || level2 != null)

    override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, errorData: Abstract.ErrorData?, expectedType: Any?) =
        checkReference(data, referent, expectedType, true)

    override fun visitLam(data: Any?, parameters: Collection<Abstract.Parameter>, body: Abstract.Expression?, errorData: Abstract.ErrorData?, expectedType: Any?) {
        if (expectedType == null) {
            return
        }

        var expectedExpr = expectedType as? Abstract.Expression ?: (expectedType as? ExpectedTypeVisitor.Substituted)?.expr
        if (expectedExpr == null) {
            typeMismatchFull(toString(expectedType), "a pi type")
            return
        }

        var currentNumberOfParameters = 0
        val visitor = object : ExpectedTypeVisitor.GetKindDefVisitor() {
            override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?): Kind {
                currentNumberOfParameters += parameters.sumBy { it.referableList.size }
                expectedExpr = codomain
                return Kind.PI
            }
        }

        val expectedNumberOfParameters = parameters.sumBy { it.referableList.size }
        while (currentNumberOfParameters < expectedNumberOfParameters) {
            val kind = expectedExpr?.accept(visitor, null)
            if (kind == null || !kind.isWHNF() || kind == GetKindVisitor.Kind.REFERENCE && expectedType is ExpectedTypeVisitor.Substituted || hasCoerce(visitor.def, true, ExpectedTypeVisitor.CoerceType.PI)) {
                return
            }
            if (kind != GetKindVisitor.Kind.PI) {
                typeMismatchFull(toString(expectedType), "a pi type with " + expectedNumberOfParameters + if (expectedNumberOfParameters == 1) " parameter" else " parameters")
                return
            }
        }
    }

    private fun checkIsUniverse(expectedType: Any?) {
        if (expectedType == null || expectedType == ExpectedTypeVisitor.Universe) {
            return
        }

        val visitor = ExpectedTypeVisitor.GetKindDefVisitor()
        val kind = getKind(expectedType, visitor)
        if (kind == GetKindVisitor.Kind.UNIVERSE || kind != null && !kind.isWHNF() || hasCoerce(visitor.def, true, ExpectedTypeVisitor.CoerceType.UNIVERSE)) {
            return
        }

        typeMismatchFull(toString(expectedType), "a universe")
    }

    override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, expectedType: Any?) {
        checkIsUniverse(expectedType)
    }

    override fun visitUniverse(data: Any?, pLevelNum: Int?, hLevelNum: Int?, pLevel: Abstract.LevelExpression?, hLevel: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, expectedType: Any?) {
        checkIsUniverse(expectedType)
    }

    override fun visitInferHole(data: Any?, errorData: Abstract.ErrorData?, expectedType: Any?) {}

    override fun visitGoal(data: Any?, name: String?, expression: Abstract.Expression?, errorData: Abstract.ErrorData?, expectedType: Any?) {
        val expectedTypeString = if (expectedType == null) "" else "Expected type: " + (if (expectedType is PsiElement) expectedType.text else toString(expectedType))
        holder.createAnnotation(HighlightSeverity.WARNING, element.textRange, if (expectedTypeString != "") expectedTypeString else "Goal", "Goal" + if (expectedTypeString != "") "<br>$expectedTypeString" else "")
    }

    override fun visitTuple(data: Any?, fields: Collection<Abstract.Expression>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        if (expectedType == null) {
            return
        }

        if (expectedType is ExpectedTypeVisitor.Sigma) {
            if (fields.size < expectedType.projection) {
                typeMismatchFull(expectedType.toString(), ExpectedTypeVisitor.Sigma.toString(fields.size))
            }
            return
        }

        val visitor = object : ExpectedTypeVisitor.GetKindDefVisitor() {
            override fun visitSigma(data: Any?, parameters: Collection<Abstract.Parameter>, errorData: Abstract.ErrorData?, params: Void?): Kind {
                if (parameters.sumBy { it.referableList.size } != fields.size) {
                    typeMismatchFull(toString(expectedType), ExpectedTypeVisitor.Sigma.toString(fields.size))
                }
                return Kind.SIGMA
            }
        }
        val kind = getKind(expectedType, visitor)
        if (kind == GetKindVisitor.Kind.SIGMA || kind != null && !kind.isWHNF() || hasCoerce(visitor.def, true, ExpectedTypeVisitor.CoerceType.SIGMA)) {
            return
        }

        typeMismatchFull(toString(expectedType), "a sigma type")
    }

    override fun visitSigma(data: Any?, parameters: Collection<Abstract.Parameter>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        checkIsUniverse(expectedType)
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
                val visitor = ExpectedTypeVisitor.GetKindDefVisitor()
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