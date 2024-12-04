package org.arend.inspection

import org.arend.ext.concrete.ConcreteSourceNode
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.DefinableMetaDefinition
import org.arend.typechecking.visitor.VoidConcreteVisitor
import java.util.LinkedList

open class ArendInspectionConcreteVisitor : VoidConcreteVisitor<Void>() {
    private val parents: LinkedList<ConcreteSourceNode> = LinkedList()

    protected val parent: ConcreteSourceNode?
        get() = parents.peek()

    protected fun parent(index: Int): ConcreteSourceNode? = parents.getOrNull(index)

    override fun visitFunction(def: Concrete.BaseFunctionDefinition?, params: Void?): Void? {
        parents.push(def)
        super.visitFunction(def, params)
        parents.pop()
        return null
    }

    override fun visitMeta(def: DefinableMetaDefinition?, params: Void?): Void? {
        parents.push(def)
        super.visitMeta(def, params)
        parents.pop()
        return null
    }

    override fun visitData(def: Concrete.DataDefinition?, params: Void?): Void? {
        parents.push(def)
        super.visitData(def, params)
        parents.pop()
        return null
    }

    override fun visitConstructor(def: Concrete.Constructor?, params: Void?) {
        parents.push(def)
        super.visitConstructor(def, params)
        parents.pop()
    }

    override fun visitClass(def: Concrete.ClassDefinition?, params: Void?): Void? {
        parents.push(def)
        super.visitClass(def, params)
        parents.pop()
        return null
    }

    override fun visitClassField(field: Concrete.ClassField?, params: Void?) {
        parents.push(field)
        super.visitClassField(field, params)
        parents.pop()
    }

    override fun visitApp(expr: Concrete.AppExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitApp(expr, params)
        parents.pop()
        return null
    }

    override fun visitFieldCall(expr: Concrete.FieldCallExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitFieldCall(expr, params)
        parents.pop()
        return null
    }

    override fun visitReference(expr: Concrete.ReferenceExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitReference(expr, params)
        parents.pop()
        return null
    }

    override fun visitThis(expr: Concrete.ThisExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitThis(expr, params)
        parents.pop()
        return null
    }

    override fun visitParameters(parameters: MutableList<out Concrete.Parameter>?, params: Void?) {
        // TODO extract the method that visits a single parameter and push parent there.
        super.visitParameters(parameters, params)
    }

    override fun visitLam(expr: Concrete.LamExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitLam(expr, params)
        parents.pop()
        return null
    }

    override fun visitPi(expr: Concrete.PiExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitPi(expr, params)
        parents.pop()
        return null
    }

    override fun visitUniverse(expr: Concrete.UniverseExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitUniverse(expr, params)
        parents.pop()
        return null
    }

    override fun visitHole(expr: Concrete.HoleExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitHole(expr, params)
        parents.pop()
        return null
    }

    override fun visitApplyHole(expr: Concrete.ApplyHoleExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitApplyHole(expr, params)
        parents.pop()
        return null
    }

    override fun visitGoal(expr: Concrete.GoalExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitGoal(expr, params)
        parents.pop()
        return null
    }

    override fun visitTuple(expr: Concrete.TupleExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitTuple(expr, params)
        parents.pop()
        return null
    }

    override fun visitSigma(expr: Concrete.SigmaExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitSigma(expr, params)
        parents.pop()
        return null
    }

    override fun visitBinOpSequence(expr: Concrete.BinOpSequenceExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitBinOpSequence(expr, params)
        parents.pop()
        return null
    }

    override fun visitPattern(pattern: Concrete.Pattern?, params: Void?) {
        parents.push(pattern)
        super.visitPattern(pattern, params)
        parents.pop()
    }

    override fun visitClauses(clauses: MutableList<out Concrete.Clause>?, params: Void?) {
        // TODO extract the method that visits a single clause and push parent there.
        super.visitClauses(clauses, params)
    }

    override fun visitCase(expr: Concrete.CaseExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitCase(expr, params)
        parents.pop()
        return null
    }

    override fun visitEval(expr: Concrete.EvalExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitEval(expr, params)
        parents.pop()
        return null
    }

    override fun visitBox(expr: Concrete.BoxExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitBox(expr, params)
        parents.pop()
        return null
    }

    override fun visitProj(expr: Concrete.ProjExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitProj(expr, params)
        parents.pop()
        return null
    }

    override fun visitClassFieldImpl(classFieldImpl: Concrete.ClassFieldImpl?, params: Void?) {
        parents.push(classFieldImpl)
        super.visitClassFieldImpl(classFieldImpl, params)
        parents.pop()
    }

    override fun visitElements(elements: MutableList<out Concrete.ClassElement>?, params: Void?) {
        // TODO extract the method that visits Concrete.OverriddenField and push parent there.
        super.visitElements(elements, params)
    }

    override fun visitClassExt(expr: Concrete.ClassExtExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitClassExt(expr, params)
        parents.pop()
        return null
    }

    override fun visitNew(expr: Concrete.NewExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitNew(expr, params)
        parents.pop()
        return null
    }

    override fun visitLet(expr: Concrete.LetExpression?, params: Void?): Void? {
        parents.push(expr)
        // TODO extract the method that visits Concrete.LetClause and push parent there.
        super.visitLet(expr, params)
        parents.pop()
        return null
    }

    override fun visitNumericLiteral(expr: Concrete.NumericLiteral?, params: Void?): Void? {
        parents.push(expr)
        super.visitNumericLiteral(expr, params)
        parents.pop()
        return null
    }

    override fun visitStringLiteral(expr: Concrete.StringLiteral?, params: Void?): Void? {
        parents.push(expr)
        super.visitStringLiteral(expr, params)
        parents.pop()
        return null
    }

    override fun visitTyped(expr: Concrete.TypedExpression?, params: Void?): Void? {
        parents.push(expr)
        super.visitTyped(expr, params)
        parents.pop()
        return null
    }
}