package org.arend.quickfix.implementCoClause

import com.intellij.psi.PsiElement
import org.arend.term.concrete.Concrete
import org.arend.typechecking.visitor.VoidConcreteVisitor

class IntentionBackEndVisitor : VoidConcreteVisitor<Void>() {
    override fun visitFunction(def: Concrete.BaseFunctionDefinition, params: Void?): Void? {
        super.visitFunction(def, params)
        doAnnotate(def.data.abstractReferable as? PsiElement)
        return null
    }

    override fun visitClassFieldImpl(classFieldImpl: Concrete.ClassFieldImpl, params: Void?) {
        super.visitClassFieldImpl(classFieldImpl, params)
        doAnnotate(classFieldImpl.data as? PsiElement)
    }

    override fun visitClassExt(expr: Concrete.ClassExtExpression, params: Void?): Void? {
        super.visitClassExt(expr, params)
        doAnnotate(expr.data as? PsiElement)
        return null
    }

    override fun visitNew(expr: Concrete.NewExpression, params: Void?): Void? {
        super.visitNew(expr, params)
        if (expr.expression !is Concrete.ClassExtExpression) doAnnotate(expr.data as? PsiElement)
        return null
    }
}