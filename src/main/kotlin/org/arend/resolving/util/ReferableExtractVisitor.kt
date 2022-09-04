package org.arend.resolving.util

import org.arend.ext.reference.DataContainer
import org.arend.naming.reference.*
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.ClassFieldImplScope
import org.arend.naming.scope.Scope
import org.arend.psi.ext.*
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.concrete.Concrete


class ReferableExtractVisitor(private val requiredAdditionalInfo: Boolean = false, isExpr: Boolean = false) : BaseAbstractExpressionVisitor<Void?, Referable?>(null) {
    private enum class Mode { TYPE, EXPRESSION, NONE }
    private var mode: Mode = if (isExpr) Mode.EXPRESSION else Mode.NONE
    var argumentsExplicitness: MutableList<Boolean> = ArrayList()
        private set
    var implementedFields: MutableSet<FieldReferable> = HashSet()
        private set

    fun findClassReference(referent: Referable?, originalScope: Scope): ClassReferable? {
        mode = Mode.EXPRESSION
        var ref: Referable? = referent
        var visited: MutableSet<Referable>? = null
        var scope = originalScope
        while (true) {
            if (ref == null) {
                return null
            }
            ref = ExpressionResolveNameVisitor.resolve(ref, scope)
            if (ref is ClassReferable) {
                return ref
            }

            val term = when (ref) {
                is ArendDefFunction -> ref.body?.expr
                is ArendDefMeta -> ref.expr
                else -> null
            } ?: return null

            if (visited == null) {
                visited = mutableSetOf(ref)
            } else {
                if (!visited.add(ref)) {
                    return null
                }
            }

            if (requiredAdditionalInfo) {
                argumentsExplicitness.clear()
                implementedFields.clear()
            }
            ref = term.accept(this, null)
            scope = term.scope
        }
    }

    fun findClassReferable(expr: ArendExpr): ClassReferable? {
        mode = Mode.TYPE
        return findClassReference(expr.accept(this, null), expr.scope)
    }

    fun findReferableInType(expr: ArendExpr): Referable? {
        mode = Mode.TYPE
        return findReferable(expr)
    }

    fun findReferable(expr: Abstract.Expression?): Referable? {
        val ref = expr?.accept(this, null)?.underlyingReferable
        if (ref is PsiReferable) {
            return ref
        }
        if (ref is DataContainer) {
            val data = (ref as? DataContainer)?.data
            if (data is ArendLongName) {
                return data.refIdentifierList.lastOrNull()?.reference?.resolve() as? Referable
            }
        }
        if (ref is UnresolvedReference && expr is ArendCompositeElement) {
            return ExpressionResolveNameVisitor.resolve(ref, expr.scope)
        }
        return ref
    }

    override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, params: Void?): Referable? {
        var expr = parseBinOp(left, sequence)
        if (expr is Concrete.AppExpression) {
            if (requiredAdditionalInfo) {
                argumentsExplicitness.addAll(0, expr.arguments.map { it.isExplicit })
            }
            expr = expr.function
        }
        return (expr as? Concrete.ReferenceExpression)?.referent
    }

    override fun visitReference(data: Any?, referent: Referable, fixity: Fixity?, pLevels: Collection<Abstract.LevelExpression>?, hLevels: Collection<Abstract.LevelExpression>?, params: Void?) = referent

    override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, params: Void?) = referent

    override fun visitLam(data: Any?, parameters: Collection<Abstract.LamParameter>, body: Abstract.Expression?, params: Void?): Referable? {
        if (mode != Mode.EXPRESSION || body == null) {
            return null
        }

        if (requiredAdditionalInfo) {
            val numberOfParameters = parameters.sumOf { if (it is Abstract.Parameter && it.isExplicit) it.referableList.size else 0 }
            if (numberOfParameters < argumentsExplicitness.size) {
                argumentsExplicitness.subList(0, numberOfParameters).clear()
            } else {
                argumentsExplicitness.clear()
            }
        }

        return body.accept(this, null)
    }

    override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, params: Void?): Referable? {
        if (mode != Mode.TYPE || codomain == null) {
            return null
        }

        for (parameter in parameters) {
            if (parameter.isExplicit) {
                return null
            }
        }

        return codomain.accept(this, null)
    }

    override fun visitClassExt(data: Any?, isNew: Boolean, evalKind: Abstract.EvalKind?, baseClass: Abstract.Expression?, coclausesData: Any?, implementations: MutableCollection<out Abstract.ClassFieldImpl>?, sequence: MutableCollection<out Abstract.BinOpSequenceElem>, clauses: Abstract.FunctionClauses?, params: Void?): Referable? =
        if (isNew || evalKind != null || !sequence.isEmpty() || baseClass == null || clauses != null) {
            null
        } else {
            if (requiredAdditionalInfo) {
                val newFields = implementations?.mapNotNull { (it as? CoClauseBase)?.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? LocatedReferable }
                if (newFields != null) {
                    for (newField in newFields) {
                        if (newField is FieldReferable) {
                            implementedFields.add(newField)
                        } else if (newField is ClassReferable) {
                            ClassFieldImplScope(newField, false).find {
                                if (it is FieldReferable) {
                                    implementedFields.add(it)
                                }
                                false
                            }
                        }
                    }
                }
            }
            baseClass.accept(this, null)
        }
}