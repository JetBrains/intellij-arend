package org.vclang.typing

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.reference.TypedReferable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.typechecking.error.local.ArgInferenceError
import org.vclang.psi.*
import org.vclang.psi.ext.VcCoClauseImplMixin
import org.vclang.psi.ext.impl.ClassFieldImplAdapter
import org.vclang.psi.ext.impl.DefinitionAdapter
import java.math.BigInteger
import java.util.*


class ExpectedTypeVisitor(private val element: VcExpr, private val holder: AnnotationHolder?) : AbstractExpressionVisitor<Void,Any>, AbstractDefinitionVisitor<Any> {
    object InferHoleExpression : Abstract.SourceNodeImpl(), Abstract.Expression {
        override fun getData() = this

        override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
            visitor.visitInferHole(this, null, params)
    }

    class ParameterImpl(private val isExplicit: Boolean, private val referables: List<Referable>, private val type: Abstract.Expression?) : Abstract.SourceNodeImpl(), Abstract.Parameter {
        override fun getData() = this

        override fun isExplicit() = isExplicit

        override fun getReferableList() = referables

        override fun getType() = type
    }

    class PiImpl(private val parameters: Collection<Abstract.Parameter>, private val codomain: Abstract.Expression?) : Abstract.SourceNodeImpl(), Abstract.Expression {
        override fun getData() = this

        override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
            visitor.visitPi(this, parameters, codomain, null, params)

        override fun toString() = "a pi type"
    }

    class Sigma(private val projections: Collection<Int>) {
        override fun toString(): String {
            val fields = projections.first()
            return "a sigma type with " + fields + (if (fields == 1) " parameter" else " parameters")
        }
    }

    class Substituted(val expr: Abstract.Expression) {
        override fun toString(): String = (expr as? VcExpr)?.text ?: expr.toString()
    }

    class Definition(val def: VcDefinition) {
        override fun toString() = def.textRepresentation()
    }

    object Data {
        override fun toString() = "a data definition"
    }

    object Universe : Abstract.SourceNodeImpl(), Abstract.Expression {
        override fun getData() = this

        override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
            visitor.visitUniverse(this, null, null, null, null, null, params)

        override fun toString() = "a universe"
    }

    interface Error {
        fun createErrorAnnotation(element: PsiElement, holder: AnnotationHolder)
    }

    class TooManyArgumentsError(private val def: String?, private val numberOfParameters: Int) : Error {
        override fun createErrorAnnotation(element: PsiElement, holder: AnnotationHolder) {
            val msg = when {
                def == null -> ""
                numberOfParameters == 0 -> "$def does not have parameters"
                else -> def + " has only " + numberOfParameters + if (numberOfParameters == 1) " parameter" else " parameters"
            }
            holder.createAnnotation(HighlightSeverity.ERROR, element.textRange, toString() + if (msg == "") "" else "; $msg", toString() + if (msg == "") "" else "<br>$msg")
        }

        override fun toString() = "Too many arguments"
    }

    class ImplicitArgumentError(private val def: String, private val argNumber: Int) : Error {
        override fun createErrorAnnotation(element: PsiElement, holder: AnnotationHolder) {
            holder.createErrorAnnotation(element, toString())
        }

        override fun toString() = ArgInferenceError.ordinal(argNumber) + " argument to " + def + " must be explicit"
    }

    companion object {
        open class GetKindDefVisitor : GetKindVisitor() {
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

        enum class CoerceType {
            UNIVERSE { override fun toGetKind() = GetKindVisitor.Kind.UNIVERSE },
            PI { override fun toGetKind() = GetKindVisitor.Kind.PI },
            SIGMA { override fun toGetKind() = GetKindVisitor.Kind.SIGMA },
            ANY;

            open fun toGetKind(): GetKindVisitor.Kind? = null
        }

        fun hasCoerce(def: VcDefinition?, fromOther: Boolean, coerceType: CoerceType): Boolean {
            if (def == null) {
                return false
            }

            if (!fromOther && def is VcDefClass) {
                val visited = HashSet<ClassReferable>()
                val toVisit = ArrayDeque<ClassReferable>()
                toVisit.add(def)
                while (!toVisit.isEmpty()) {
                    val cur = toVisit.pop()
                    if (!visited.add(cur)) {
                        continue
                    }
                    if (cur is Abstract.ClassDefinition) {
                        val param = cur.parameters.firstOrNull { it.isExplicit }
                        if (param != null) {
                            if (coerceType == CoerceType.ANY) {
                                return true
                            }
                            val type = param.type ?: break
                            val visitor = GetKindDefVisitor()
                            val kind = type.accept(visitor, null)
                            if (coerceType.toGetKind() == kind || !kind.isWHNF() || visitor.def != null) {
                                return true
                            } else {
                                break
                            }
                        }
                    }
                    toVisit.addAll(cur.superClassReferences)
                }
            }

            val stats = (def as? DefinitionAdapter<*>)?.getWhere()?.statementList ?: return false
            for (stat in stats) {
                val statDef = stat.definition
                if (statDef is VcDefFunction && statDef.coerceKw != null) {
                    if (coerceType == CoerceType.ANY) {
                        return true
                    }
                    val type = if (fromOther) {
                        statDef.nameTeleList.lastOrNull()?.type ?: continue
                    } else {
                        statDef.expr ?: return true
                    }
                    val visitor = GetKindDefVisitor()
                    val kind = type.accept(visitor, null)
                    if (coerceType.toGetKind() == kind || !kind.isWHNF() || visitor.def != null) {
                        return true
                    }
                }
            }
            return false
        }

        fun getParameterType(parameters: Collection<Abstract.Parameter>, expr: Any?, paramExplicitness: List<Boolean>, defName: String): Any? {
            var i = 0
            for (parameter in parameters) {
                val isExplicit = parameter.isExplicit
                val size = parameter.referableList.size
                for (j in 0 until size) {
                    when {
                        isExplicit == paramExplicitness[i] ->
                            if (i == paramExplicitness.size - 1) {
                                return parameter.type
                            } else {
                                i++
                            }
                        isExplicit -> return if (i == paramExplicitness.size - 1) ImplicitArgumentError(defName, i + 1) else null
                    }
                }
            }
            return when (expr) {
                is Abstract.Expression -> getParameterType(expr, paramExplicitness, i, defName)
                is Error -> expr
                else -> null
            }
        }

        fun getParameterType(expr: Abstract.Expression?, paramExplicitness: List<Boolean>, defName: String) =
            getParameterType(expr, paramExplicitness, 0, defName)

        private fun getParameterType(expr: Abstract.Expression?, paramExplicitness: List<Boolean>, startIndex: Int, defName: String): Any? {
            var result: Any? = null
            var i = startIndex
            var exprVar = expr
            var isDone = false

            val visitor = object : GetKindDefVisitor() {
                override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?): Kind {
                    for (parameter in parameters) {
                        val isExplicit = parameter.isExplicit
                        val size = parameter.referableList.size
                        for (j in 0 until size) {
                            if (isExplicit == paramExplicitness[i]) {
                                if (i == paramExplicitness.size - 1) {
                                    result = parameter.type
                                    isDone = true
                                    return Kind.PI
                                } else {
                                    i++
                                }
                            } else if (isExplicit) {
                                result = if (i == paramExplicitness.size - 1) ImplicitArgumentError(defName, i + 1) else null
                                isDone = true
                                return Kind.PI
                            }
                        }
                    }
                    exprVar = codomain
                    return Kind.PI
                }
            }

            while (true) {
                val kind = exprVar?.accept(visitor, null) ?: return result
                if (isDone) {
                    return result
                }
                if (kind != GetKindVisitor.Kind.PI) {
                    return if (kind.isWHNF() && !hasCoerce(visitor.def, false, CoerceType.PI)) TooManyArgumentsError(defName, i) else null
                }
            }
        }

        fun getTypeOf(parameters: Collection<Abstract.Parameter>, expr: Abstract.Expression?): Abstract.Expression? =
            if (parameters.isEmpty()) expr else PiImpl(parameters, expr)
    }

    private fun dropParameters(parameters: Collection<Abstract.Parameter>, drop: Int): Collection<Abstract.Parameter> {
        if (drop == 0) {
            return parameters
        }

        var n = drop
        var toDrop = 0
        for (parameter in parameters) {
            toDrop++
            val m = parameter.referableList.size
            when {
                n < m -> return listOf(ParameterImpl(parameter.isExplicit, parameter.referableList.drop(n), parameter.type)) + parameters.drop(toDrop)
                n == m -> return parameters.drop(toDrop)
                else -> n -= m
            }
        }
        return emptyList()
    }

    private fun reduceParameters(parameters: Collection<Abstract.Parameter>, type: Abstract.Expression?, holder: AnnotationHolder?): Abstract.Expression? =
        if (parameters.isEmpty()) {
            type
        } else {
            var requiredParameters = parameters.sumBy { it.referableList.size }
            var currentType = type

            while (currentType != null && requiredParameters > 0) {
                val kind = currentType.accept(object : GetKindVisitor() {
                    override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?): Kind {
                        val availableParameters = parameters.sumBy { it.referableList.size }
                        if (availableParameters <= requiredParameters) {
                            requiredParameters -= availableParameters
                            currentType = codomain
                        } else {
                            requiredParameters = 0
                            currentType = ExpectedTypeVisitor.PiImpl(dropParameters(parameters, requiredParameters), codomain)
                        }
                        return Kind.PI
                    }
                }, null)

                if (kind != GetKindVisitor.Kind.PI) {
                    if (holder != null && kind.isWHNF()) {
                        holder.createErrorAnnotation(TextRange((parameters.first() as VcNameTele).textRange.startOffset, (parameters.last() as VcNameTele).textRange.endOffset), "Too many parameters")
                    }
                    currentType = null
                    break
                }
            }

            currentType
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
                            holder?.createErrorAnnotation(TextRange(parameters.first().textRange.startOffset, parameters.last().textRange.endOffset), "Class cannot have parameters")
                        }
                        Definition(implemented)
                    }
                    is Abstract.ClassField -> reduceParameters(PsiTreeUtil.getChildrenOfTypeAsList(parent as? PsiElement, VcNameTele::class.java), implemented.resultType, holder)
                    else -> null
                }
            }
            is Abstract.LetClause -> {
                val resultType = reduceParameters(PsiTreeUtil.getChildrenOfTypeAsList(parent as? PsiElement, VcNameTele::class.java), parent.resultType, holder)
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

    override fun visitReference(data: Any?, referent: Referable, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, params: Void?) = null

    override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, errorData: Abstract.ErrorData?, params: Void?) = null

    override fun visitLam(data: Any?, parameters: Collection<Abstract.Parameter>, body: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?): Any? {
        if (element != body) {
            return null
        }

        val expectedType = (data as? VcExpr)?.let { ExpectedTypeVisitor(it, null).getExpectedType() } ?: return null
        val expectedTypeExpr = expectedType as? Abstract.Expression ?: (expectedType as? Substituted)?.expr ?: return null
        val result = reduceParameters(parameters, expectedTypeExpr, null) ?: return null
        return if (expectedType is Substituted) Substituted(result) else result
    }

    override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?) = if (element == codomain) Universe else null

    override fun visitUniverse(data: Any?, pLevelNum: Int?, hLevelNum: Int?, pLevel: Abstract.LevelExpression?, hLevel: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, params: Void?) = null

    override fun visitInferHole(data: Any?, errorData: Abstract.ErrorData?, params: Void?) = null

    override fun visitGoal(data: Any?, name: String?, expression: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?) =
        (data as? VcExpr)?.let { ExpectedTypeVisitor(it, null).getExpectedType() }

    override fun visitTuple(data: Any?, fields: Collection<Abstract.Expression>, errorData: Abstract.ErrorData?, params: Void?): Any? {
        val originalIndex = fields.indexOf(element)
        var index = originalIndex
        if (index < 0) {
            return null
        }

        val sigmaExpr = (data as? VcExpr)?.let { ExpectedTypeVisitor(it, null).getExpectedType() } as? VcSigmaExpr ?: return null
        if (sigmaExpr.typeTeleList.sumBy { it.referableList.size } != fields.size) {
            return null
        }

        for (parameter in sigmaExpr.typeTeleList) {
            index -= parameter.referableList.size
            if (index < 0) {
                val result = parameter.type ?: return null
                return if (originalIndex == 0) result else Substituted(result)
            }
        }
        return null
    }

    override fun visitSigma(data: Any?, parameters: Collection<Abstract.Parameter>, errorData: Abstract.ErrorData?, params: Void?) = null

    private fun findElement(expr: Concrete.Expression): Concrete.AppExpression? {
        if (expr is Concrete.AppExpression) {
            if (expr.function.data == element || expr.arguments.any { it.expression.data == element }) {
                return expr
            }
            val result = findElement(expr.function)
            if (result != null) {
                return result
            }
            for (argument in expr.arguments) {
                val result1 = findElement(argument.expression)
                if (result1 != null) {
                    return result1
                }
            }
        }
        return null
    }

    override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?): Any? {
        val appExpr = findElement(parseBinOp(left, sequence)) ?: return null
        if (appExpr.function.data == element) {
            return null
        }

        val ref = (appExpr.function as? Concrete.ReferenceExpression)?.referent as? TypedReferable ?: return null
        val args = appExpr.arguments
        val index = args.indexOfFirst { it.expression.data == element }
        return if (index < 0) null else ref.getParameterType(args.take(index + 1).map { it.isExplicit })
    }

    override fun visitCase(data: Any?, expressions: Collection<Abstract.Expression>, clauses: Collection<Abstract.FunctionClause>, errorData: Abstract.ErrorData?, params: Void?) =
        if (expressions.contains(element)) Data else null

    override fun visitFieldAccs(data: Any?, expression: Abstract.Expression, fieldAccs: Collection<Int>, errorData: Abstract.ErrorData?, params: Void?) =
        if (element == expression) Sigma(fieldAccs) else null

    override fun visitClassExt(data: Any?, isNew: Boolean, baseClass: Abstract.Expression?, implementations: Collection<Abstract.ClassFieldImpl>?, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?) =
        if (element == baseClass) null else visitBinOpSequence(data, InferHoleExpression, sequence, errorData, params)

    override fun visitLet(data: Any?, clauses: Collection<Abstract.LetClause>, expression: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?) =
        if (element == expression) (data as? VcExpr)?.let { ExpectedTypeVisitor(it, null).getExpectedType() } else null

    override fun visitNumericLiteral(data: Any?, number: BigInteger, errorData: Abstract.ErrorData?, params: Void?) = null

    override fun visitFunction(def: Abstract.FunctionDefinition): Any? {
        val resultType = def.resultType
        return when (element) {
            resultType -> Universe
            def.term -> resultType
            else -> null
        }
    }

    override fun visitData(def: Abstract.DataDefinition) = null

    override fun visitClass(def: Abstract.ClassDefinition) = null

    override fun visitInstance(def: Abstract.InstanceDefinition) = if (element == def.resultType) Universe else null
}