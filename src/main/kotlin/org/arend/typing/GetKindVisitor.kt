package org.arend.typing

import org.arend.naming.reference.ErrorReference
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.UnresolvedReference
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.ArendDefIdentifier
import org.arend.psi.ArendLetClause
import org.arend.psi.ArendLetClausePattern
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.term.concrete.Concrete
import java.math.BigInteger


open class GetKindVisitor : AbstractExpressionVisitor<Void, GetKindVisitor.Kind> {
    enum class Kind {
        LAMBDA { override fun isWHNF() = true },
        PI { override fun isWHNF() = true },
        UNIVERSE { override fun isWHNF() = true },
        TUPLE { override fun isWHNF() = true },
        SIGMA { override fun isWHNF() = true },
        CLASS_EXT { override fun isWHNF() = true },
        NEW { override fun isWHNF() = true },
        CONSTRUCTOR { override fun isWHNF() = true }, // maybe with arguments
        DATA { override fun isWHNF() = true }, // maybe with arguments
        CLASS { override fun isWHNF() = true }, // maybe with arguments
        REFERENCE { override fun isWHNF() = true }, // maybe with arguments
        CONSTRUCTOR_WITH_CONDITIONS, // maybe with arguments
        APP, HOLE, GOAL, CASE, PROJ, LET, LET_CLAUSE, NUMBER, UNRESOLVED_REFERENCE, FIELD, FUNCTION, INSTANCE, UNKNOWN;

        open fun isWHNF(): Boolean = false
    }

    open fun getReferenceKind(ref: Referable) =
        when (ref) {
            is UnresolvedReference, is ErrorReference -> Kind.UNRESOLVED_REFERENCE
            is Abstract.ClassField -> Kind.FIELD
            is Abstract.Constructor -> if (ref.clauses.isEmpty()) Kind.CONSTRUCTOR else Kind.CONSTRUCTOR_WITH_CONDITIONS
            is Abstract.DataDefinition -> Kind.DATA
            is Abstract.ClassDefinition -> Kind.CLASS
            is Abstract.FunctionDefinition -> if (ref.isInstance) Kind.INSTANCE else Kind.FUNCTION
            is Abstract.LetClause -> Kind.LET_CLAUSE
            else -> {
                val parent = (ref as? ArendDefIdentifier)?.parent
                if (parent is ArendLetClause || parent is ArendLetClausePattern) Kind.LET_CLAUSE else Kind.REFERENCE
            }
        }

    private fun getReferenceKind(data: Any?, referent: Referable) : Kind {
        val ref = (data as? ArendCompositeElement)?.scope?.let { ExpressionResolveNameVisitor.resolve(referent, it) } ?: referent
        return getReferenceKind(if (ref is GlobalReferable) PsiLocatedReferable.fromReferable(ref) ?: ref else ref)
    }

    override fun visitErrors() = false
    override fun visitReference(data: Any?, referent: Referable, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, params: Void?) = getReferenceKind(data, referent)
    override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, errorData: Abstract.ErrorData?, params: Void?) = getReferenceKind(data, referent)
    override fun visitThis(data: Any?) = Kind.REFERENCE
    override fun visitLam(data: Any?, parameters: Collection<Abstract.Parameter>, body: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?) = Kind.LAMBDA
    override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?) = Kind.PI
    override fun visitUniverse(data: Any?, pLevelNum: Int?, hLevelNum: Int?, pLevel: Abstract.LevelExpression?, hLevel: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, params: Void?) = Kind.UNIVERSE
    override fun visitTuple(data: Any?, fields: Collection<Abstract.Expression>, errorData: Abstract.ErrorData?, params: Void?) = Kind.TUPLE
    override fun visitSigma(data: Any?, parameters: Collection<Abstract.Parameter>, errorData: Abstract.ErrorData?, params: Void?) = Kind.SIGMA
    override fun visitClassExt(data: Any?, isNew: Boolean, baseClass: Abstract.Expression?, implementations: Collection<Abstract.ClassFieldImpl>?, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?) = if (isNew) Kind.NEW else Kind.CLASS_EXT
    override fun visitInferHole(data: Any?, errorData: Abstract.ErrorData?, params: Void?) = Kind.HOLE
    override fun visitGoal(data: Any?, name: String?, expression: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?) = Kind.GOAL
    override fun visitCase(data: Any?, caseArgs: Collection<Abstract.CaseArgument>, resultType: Abstract.Expression?, resultTypeLevel: Abstract.Expression?, clauses: Collection<Abstract.FunctionClause>, errorData: Abstract.ErrorData?, params: Void?) = Kind.CASE
    override fun visitFieldAccs(data: Any?, expression: Abstract.Expression, fieldAccs: MutableCollection<Int>, errorData: Abstract.ErrorData?, params: Void?) = Kind.PROJ
    override fun visitLet(data: Any?, clauses: Collection<Abstract.LetClause>, expression: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?) = Kind.LET
    override fun visitNumericLiteral(data: Any?, number: BigInteger, errorData: Abstract.ErrorData?, params: Void?) = Kind.NUMBER
    override fun visitTyped(data: Any?, expr: Abstract.Expression, type: Abstract.Expression, errorData: Abstract.ErrorData?, params: Void?): Kind = expr.accept(this, null)

    override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?): Kind {
        val expr = parseBinOp(left, sequence)
        if (expr is Concrete.ReferenceExpression) {
            return getReferenceKind(null, expr.referent)
        }

        val reference = ((expr as? Concrete.AppExpression)?.function as? Concrete.ReferenceExpression)?.referent
        return if (reference == null) Kind.APP else getReferenceKind(reference)
    }
}