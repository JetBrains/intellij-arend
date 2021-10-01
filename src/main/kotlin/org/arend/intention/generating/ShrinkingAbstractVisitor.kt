package org.arend.intention.generating

import org.arend.naming.reference.Referable
import org.arend.resolving.util.parseBinOp
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.term.concrete.Concrete
import java.math.BigInteger

class ShrinkingAbstractVisitor : AbstractExpressionVisitor<Unit, String> {
    override fun visitReference(data: Any?, referent: Referable, fixity: Fixity?, pLevels: MutableCollection<out Abstract.LevelExpression>?, hLevels: MutableCollection<out Abstract.LevelExpression>?, params: Unit?): String =
            referent.refName

    override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, params: Unit?): String = referent.refName

    override fun visitThis(data: Any?, params: Unit?): String = "\\this"

    override fun visitLam(data: Any?, parameters: MutableCollection<out Abstract.LamParameter>, body: Abstract.Expression?, params: Unit?): String =
            """\lam ... => ${body?.accept(this, Unit) ?: "INVALID"}"""

    override fun visitPi(data: Any?, parameters: MutableCollection<out Abstract.Parameter>, codomain: Abstract.Expression?, params: Unit?): String =
            """\Pi ... -> ${codomain?.accept(this, Unit) ?: "INVALID"}"""

    override fun visitUniverse(data: Any?, pLevelNum: Int?, hLevelNum: Int?, pLevel: Abstract.LevelExpression?, hLevel: Abstract.LevelExpression?, params: Unit?): String =
            """\Type"""

    override fun visitApplyHole(data: Any?, params: Unit?): String = "_"

    override fun visitInferHole(data: Any?, params: Unit?): String = "_"

    override fun visitGoal(data: Any?, name: String?, expression: Abstract.Expression?, params: Unit?): String = "{?}"

    override fun visitTuple(data: Any?, fields: MutableCollection<out Abstract.Expression>, trailingComma: Any?, params: Unit?): String =
            if (fields.size == 1) {
                "(${fields.single().accept(this, Unit)})"
            } else {
                fields.joinToString(", ", "(", ")") { "..." }
            }

    override fun visitSigma(data: Any?, parameters: MutableCollection<out Abstract.Parameter>, params: Unit?): String =
            """\Sigma (...) (...)"""

    override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: MutableCollection<out Abstract.BinOpSequenceElem>, params: Unit?): String {
        val parsed = parseBinOp(left, sequence)
        return if (parsed is Concrete.AppExpression) {
            (parsed.function.data as Abstract.Expression).accept(this, Unit) + parsed.arguments.joinToString(", ") { "(...)" }
        } else {
            left.accept(this, Unit)
        }
    }

    override fun visitCase(data: Any?, isSFunc: Boolean, evalKind: Abstract.EvalKind?, arguments: MutableCollection<out Abstract.CaseArgument>, resultType: Abstract.Expression?, resultTypeLevel: Abstract.Expression?, clauses: MutableCollection<out Abstract.FunctionClause>, params: Unit?): String {
        return """\case ${arguments.map { it.expression?.accept(this, Unit) }.joinToString(", ")} \with { ... }"""
    }

    override fun visitFieldAccs(data: Any?, expression: Abstract.Expression, fieldAccs: MutableCollection<Int>, params: Unit?): String {
        return "${expression.accept(this, Unit)}.${fieldAccs.joinToString(".") { it.toString() }}"
    }

    override fun visitClassExt(data: Any?, isNew: Boolean, evalKind: Abstract.EvalKind?, baseClass: Abstract.Expression?, coclausesData: Any?, implementations: MutableCollection<out Abstract.ClassFieldImpl>?, sequence: MutableCollection<out Abstract.BinOpSequenceElem>, clauses: Abstract.FunctionClauses?, params: Unit?): String {
        return "${baseClass?.accept(this, Unit) ?: "INVALID"} { ... }"
    }

    override fun visitLet(data: Any?, isHave: Boolean, isStrict: Boolean, clauses: MutableCollection<out Abstract.LetClause>, expression: Abstract.Expression?, params: Unit?): String {
        val kw = when (isHave) {
            true -> "\\have"
            false -> "\\let"
        }
        val actualKw = if (isStrict) "$kw!" else kw
        return """$actualKw ... \in ${expression?.accept(this, Unit) ?: "INVALID"}"""
    }

    override fun visitNumericLiteral(data: Any?, number: BigInteger, params: Unit?): String {
        return number.toString()
    }

    override fun visitStringLiteral(data: Any?, unescapedString: String, params: Unit?): String {
        return unescapedString
    }

    override fun visitTyped(data: Any?, expr: Abstract.Expression, type: Abstract.Expression, params: Unit?): String {
        return expr.accept(this, Unit) + " : ..."
    }
}