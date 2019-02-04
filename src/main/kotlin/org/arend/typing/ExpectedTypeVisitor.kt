package org.arend.typing

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TypedReferable
import org.arend.psi.*
import org.arend.psi.ext.ArendCoClauseImplMixin
import org.arend.psi.ext.impl.ClassFieldImplAdapter
import org.arend.psi.ext.impl.DefinitionAdapter
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.term.concrete.Concrete
import org.arend.typechecking.error.local.ArgInferenceError
import java.math.BigInteger
import java.util.*


class ExpectedTypeVisitor(private val element: ArendExpr, private val holder: AnnotationHolder?) : AbstractExpressionVisitor<Void,Any>, AbstractDefinitionVisitor<Any> {
    object InferHoleExpression : Abstract.SourceNodeImpl(), Abstract.Expression {
        override fun getData() = this

        override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
            visitor.visitInferHole(this, null, params)
    }

    class ParameterImpl(private val isExplicit: Boolean, private val referables: List<Referable?>, private val type: Abstract.Expression?) : Abstract.SourceNodeImpl(), Abstract.Parameter {
        override fun getData() = this

        override fun isExplicit() = isExplicit

        override fun getReferableList() = referables

        override fun getType() = type
    }

    class ReferenceImpl(private val referable: Referable) : Abstract.SourceNodeImpl(), Abstract.Expression {
        override fun getData() = this

        override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
            visitor.visitReference(this, referable, null, null, null, params)

        override fun toString() = referable.textRepresentation()
    }

    class PiImpl(private val parameters: Collection<Abstract.Parameter>, private val codomain: Abstract.Expression?) : Abstract.SourceNodeImpl(), Abstract.Expression {
        override fun getData() = this

        override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
            visitor.visitPi(this, parameters, codomain, null, params)

        override fun toString() = "a pi type"
    }

    class Sigma(val projection: Int) {
        override fun toString() = toString(projection)

        companion object {
            fun toString(p: Int) = "a sigma type with " + p + (if (p == 1) " parameter" else " parameters")
        }
    }

    class Substituted(val expr: Abstract.Expression) {
        override fun toString(): String = (expr as? ArendExpr)?.text ?: expr.toString()
    }

    class Definition(val def: ArendDefinition) {
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

    open class GetKindDefVisitor : GetKindVisitor() {
        var def: ArendDefinition? = null

        override fun getReferenceKind(ref: Referable): GetKindVisitor.Kind {
            if (ref is ArendDefinition) {
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

    companion object {
        fun hasCoerce(def: ArendDefinition?, fromOther: Boolean, coerceType: CoerceType): Boolean {
            if (def == null || def !is ArendDefClass && def !is ArendDefData) {
                return false
            }

            if (!fromOther && def is ArendDefClass) {
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

            val stats = (def as? DefinitionAdapter<*>)?.where?.statementList ?: return false
            for (stat in stats) {
                val statDef = stat.definition
                if (statDef is ArendDefFunction && statDef.coerceKw != null) {
                    if (coerceType == CoerceType.ANY) {
                        return true
                    }
                    val type = if (fromOther) {
                        statDef.nameTeleList.lastOrNull()?.type ?: continue
                    } else {
                        statDef.resultType ?: return true
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
            var isSubstituted = false
            for (parameter in parameters) {
                val isExplicit = parameter.isExplicit
                for (ref in parameter.referableList) {
                    when {
                        isExplicit == paramExplicitness[i] ->
                            if (i == paramExplicitness.size - 1) {
                                val type = parameter.type
                                return if (isSubstituted && type != null) Substituted(type) else type
                            } else {
                                i++
                            }
                        isExplicit -> return if (i == paramExplicitness.size - 1) ImplicitArgumentError(defName, i + 1) else null
                    }
                    if (!isSubstituted && ref != null) {
                        isSubstituted = true
                    }
                }
            }
            return when (expr) {
                is Abstract.Expression -> getParameterType(expr, paramExplicitness, i, defName, isSubstituted)
                is Error -> expr
                else -> null
            }
        }

        fun getParameterType(expr: Abstract.Expression?, paramExplicitness: List<Boolean>, defName: String) =
            getParameterType(expr, paramExplicitness, 0, defName, false)

        private fun getParameterType(expr: Abstract.Expression?, paramExplicitness: List<Boolean>, startIndex: Int, defName: String, isSubst: Boolean): Any? {
            var result: Any? = null
            var i = startIndex
            var exprVar = expr
            var isDone = false
            var isSubstituted = isSubst

            val visitor = object : GetKindDefVisitor() {
                override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?): Kind {
                    for (parameter in parameters) {
                        val isExplicit = parameter.isExplicit
                        for (ref in parameter.referableList) {
                            if (isExplicit == paramExplicitness[i]) {
                                if (i == paramExplicitness.size - 1) {
                                    val type = parameter.type
                                    result = if (isSubstituted && type != null) Substituted(type) else type
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
                            if (!isSubstituted && ref != null) {
                                isSubstituted = true
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
                    return if (!kind.isWHNF() || isSubstituted && kind == GetKindVisitor.Kind.REFERENCE || hasCoerce(visitor.def, false, CoerceType.PI)) {
                        null
                    } else {
                        TooManyArgumentsError(defName, i)
                    }
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
                        holder.createErrorAnnotation(TextRange((parameters.first() as ArendNameTele).textRange.startOffset, (parameters.last() as ArendNameTele).textRange.endOffset), "Too many parameters")
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
            is Abstract.Constructor -> if (element == parent.resultType) Universe else null
            is Abstract.FunctionClause -> {
                val pparent = parent.topmostEquivalentSourceNode.parentSourceNode
                when (pparent) {
                    is Abstract.FunctionDefinition -> (pparent.resultType as? ArendExpr)?.let { Substituted(it) }
                    is ArendConstructor -> pparent.ancestors.filterIsInstance<ArendDefData>().firstOrNull()?.let { Definition(it) }
                    else -> null
                }
            }
            is Abstract.ClassFieldImpl -> {
                val implemented = when (parent) {
                    is ClassFieldImplAdapter -> parent.getResolvedImplementedField()
                    is ArendCoClauseImplMixin -> parent.getResolvedImplementedField()
                    else -> null
                }
                when (implemented) {
                    is ArendDefClass -> {
                        val parameters = PsiTreeUtil.getChildrenOfTypeAsList(parent as? PsiElement, ArendNameTele::class.java)
                        if (!parameters.isEmpty()) {
                            holder?.createErrorAnnotation(TextRange(parameters.first().textRange.startOffset, parameters.last().textRange.endOffset), "Class cannot have parameters")
                        }
                        Definition(implemented)
                    }
                    is Abstract.ClassField -> {
                        val typeParameters = implemented.parameters
                        reduceParameters(PsiTreeUtil.getChildrenOfTypeAsList(parent as? PsiElement, ArendNameTele::class.java), if (typeParameters.isEmpty()) implemented.resultType else PiImpl(typeParameters, implemented.resultType), holder)
                    }
                    else -> null
                }
            }
            is Abstract.LetClause -> {
                val resultType = parent.resultType
                when {
                    resultType == element -> Universe
                    parent.term == element -> resultType
                    else -> null
                }
            }
            is Abstract.ClassField -> Universe
            is Abstract.CaseArgument -> when (element) {
                parent.expression -> Data
                parent.type -> Universe
                else -> null
            }
            else -> null
        }
    }

    override fun visitErrors() = false

    override fun visitReference(data: Any?, referent: Referable, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, params: Void?) = null

    override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, errorData: Abstract.ErrorData?, params: Void?) = null

    override fun visitThis(data: Any?) = null

    override fun visitLam(data: Any?, parameters: Collection<Abstract.Parameter>, body: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?): Any? {
        if (element != body) {
            return null
        }

        val expectedType = (data as? ArendExpr)?.let { ExpectedTypeVisitor(it, null).getExpectedType() } ?: return null
        val expectedTypeExpr = expectedType as? Abstract.Expression ?: (expectedType as? Substituted)?.expr ?: return null
        val result = reduceParameters(parameters, expectedTypeExpr, null) ?: return null
        return if (expectedType is Substituted) Substituted(result) else result
    }

    override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?) = if (element == codomain) Universe else null

    override fun visitUniverse(data: Any?, pLevelNum: Int?, hLevelNum: Int?, pLevel: Abstract.LevelExpression?, hLevel: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, params: Void?) = null

    override fun visitInferHole(data: Any?, errorData: Abstract.ErrorData?, params: Void?) = null

    override fun visitGoal(data: Any?, name: String?, expression: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?) =
        (data as? ArendExpr)?.let { ExpectedTypeVisitor(it, null).getExpectedType() }

    override fun visitTuple(data: Any?, fields: Collection<Abstract.Expression>, errorData: Abstract.ErrorData?, params: Void?): Any? {
        val originalIndex = fields.indexOf(element)
        var index = originalIndex
        if (index < 0) {
            return null
        }

        val sigmaExpr = (data as? ArendExpr)?.let { ExpectedTypeVisitor(it, null).getExpectedType() } as? ArendSigmaExpr ?: return null
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

    override fun visitCase(data: Any?, caseArgs: Collection<Abstract.CaseArgument>, resultType: Abstract.Expression?, resultTypeLevel: Abstract.Expression?, clauses: Collection<Abstract.FunctionClause>, errorData: Abstract.ErrorData?, params: Void?) =
        if (element == resultType) Universe else null

    override fun visitFieldAccs(data: Any?, expression: Abstract.Expression, fieldAccs: Collection<Int>, errorData: Abstract.ErrorData?, params: Void?) =
        if (element == expression) Sigma(fieldAccs.first()) else null

    override fun visitClassExt(data: Any?, isNew: Boolean, baseClass: Abstract.Expression?, implementations: Collection<Abstract.ClassFieldImpl>?, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?) =
        if (element == baseClass) null else visitBinOpSequence(data, InferHoleExpression, sequence, errorData, params)

    override fun visitLet(data: Any?, clauses: Collection<Abstract.LetClause>, expression: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?) =
        if (element == expression) (data as? ArendExpr)?.let { ExpectedTypeVisitor(it, null).getExpectedType() } else null

    override fun visitNumericLiteral(data: Any?, number: BigInteger, errorData: Abstract.ErrorData?, params: Void?) = null

    override fun visitTyped(data: Any?, expr: Abstract.Expression, type: Abstract.Expression, errorData: Abstract.ErrorData?, params: Void?) =
        when (element) {
            expr -> type
            type -> Universe
            else -> null
        }

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