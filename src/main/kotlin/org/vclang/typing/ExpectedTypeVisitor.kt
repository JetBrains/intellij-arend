package org.vclang.typing

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.*
import org.vclang.psi.ext.VcCoClauseImplMixin
import org.vclang.psi.ext.impl.ClassFieldImplAdapter
import java.math.BigInteger


class ExpectedTypeVisitor(private val element: VcExpr, private val holder: AnnotationHolder) : AbstractExpressionVisitor<Void,Any>, AbstractDefinitionVisitor<Any> {
    class Pi(val args: Int) {
        override fun toString() = "a pi type"
    }

    class Sigma(val args: Int) {
        override fun toString() = "a sigma type"
    }

    class Substituted(val expr: VcExpr) {
        override fun toString(): String = expr.text
    }

    class Definition(val def: VcDefinition) {
        override fun toString() = def.textRepresentation()
    }

    object Universe {
        override fun toString() = "a universe"
    }

    private fun reduceParameters(parameters: List<VcNameTele>, type: Abstract.Expression): Abstract.Expression? =
        if (parameters.isEmpty()) {
            type
        } else {
            val kind = type.accept(object : GetKindVisitor() {
                override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?): Kind {
                    // TODO: remove (parameters.sumBy { it.referableList.size }) parameters of the pi expression and return the rest; report an error if the pi expression does not have enough parameters.
                    return Kind.PI
                }
            }, null)
            if (kind != GetKindVisitor.Kind.PI && kind.isWHNF()) {
                holder.createErrorAnnotation(TextRange(parameters.first().textRange.startOffset, parameters.last().textRange.endOffset), "Too many parameters")
            }
            null
        }

    fun getExpectedType(): Any? {
        var parent: Abstract.SourceNode = element.topmostEquivalentSourceNode.parentSourceNode ?: return null
        while (true) {
            parent = if (parent is Abstract.BinOpSequenceElem) {
                parent.topmostEquivalentSourceNode.parentSourceNode ?: return null
            } else if (parent is Abstract.Argument) {
                parent.topmostEquivalentSourceNode.parentSourceNode ?: return null
            } else {
                break
            }
        }

        return when (parent) {
            is Abstract.Expression -> parent.accept(this, null)
            is Abstract.Definition -> parent.accept(this)
            is Abstract.Parameter -> Universe
            is Abstract.FunctionClause -> {
                val pparent = parent.topmostEquivalentSourceNode.parentSourceNode
                when (pparent) {
                    is Abstract.FunctionDefinition -> (pparent.resultType as? VcExpr)?.let { Substituted(it) }
                    is VcConstructor -> pparent.ancestors.filterIsInstance<VcDefData>().firstOrNull()?.let { Definition(it) }
                    else -> null
                }
            }
            is Abstract.ClassFieldImpl -> {
                val implemented = when (parent) {
                    is ClassFieldImplAdapter -> parent.getResolvedImplementedField()
                    is VcCoClauseImplMixin -> parent.getResolvedImplementedField()
                    else -> null
                }
                when (implemented) {
                    is VcDefClass -> {
                        val parameters = PsiTreeUtil.getChildrenOfTypeAsList(parent as? PsiElement, VcNameTele::class.java)
                        if (!parameters.isEmpty()) {
                            holder.createErrorAnnotation(TextRange(parameters.first().textRange.startOffset, parameters.last().textRange.endOffset), "Class cannot have parameters")
                        }
                        Definition(implemented)
                    }
                    is Abstract.ClassField -> implemented.resultType?.let { reduceParameters(PsiTreeUtil.getChildrenOfTypeAsList(parent as? PsiElement, VcNameTele::class.java), it) }
                    else -> null
                }
            }
            is Abstract.LetClause -> {
                val resultType = parent.resultType?.let { reduceParameters(PsiTreeUtil.getChildrenOfTypeAsList(parent as? PsiElement, VcNameTele::class.java), it) }
                when {
                    resultType == element -> Universe
                    parent.term == element -> resultType
                    else -> null
                }
            }
            is Abstract.ClassField -> Universe
            else -> null
        }
    }

    override fun visitErrors() = false

    override fun visitApp(data: Any?, expr: Abstract.Expression, arguments: Collection<Abstract.Argument>, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitReference(data: Any?, referent: Referable, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitLam(data: Any?, parameters: Collection<Abstract.Parameter>, body: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitUniverse(data: Any?, pLevelNum: Int?, hLevelNum: Int?, pLevel: Abstract.LevelExpression?, hLevel: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitInferHole(data: Any?, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitGoal(data: Any?, name: String?, expression: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitTuple(data: Any?, fields: Collection<Abstract.Expression>, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitSigma(data: Any?, parameters: Collection<Abstract.Parameter>, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitCase(data: Any?, expressions: Collection<Abstract.Expression>, clauses: Collection<Abstract.FunctionClause>, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitFieldAccs(data: Any?, expression: Abstract.Expression, fieldAccs: Collection<Int>, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitClassExt(data: Any?, isNew: Boolean, baseClass: Abstract.Expression?, implementations: Collection<Abstract.ClassFieldImpl>?, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitLet(data: Any?, clauses: Collection<Abstract.LetClause>, expression: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitNumericLiteral(data: Any?, number: BigInteger, errorData: Abstract.ErrorData?, params: Void?): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitFunction(def: Abstract.FunctionDefinition): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitData(def: Abstract.DataDefinition): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitClass(def: Abstract.ClassDefinition): Any? {
        // TODO("not implemented")
        return null
    }

    override fun visitInstance(def: Abstract.InstanceDefinition): Any? {
        // TODO("not implemented")
        return null
    }
}