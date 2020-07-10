package org.arend.quickfix.implementCoClause

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiElement
import org.arend.quickfix.implementCoClause.doAnnotate
import org.arend.term.concrete.Concrete
import org.arend.typechecking.visitor.VoidConcreteVisitor

class IntentionBackEndVisitor(private val holder: AnnotationHolder?) : VoidConcreteVisitor<Void, Void>() {
    override fun visitFunction(def: Concrete.BaseFunctionDefinition, params: Void?): Void? {
        super.visitFunction(def, params)
        doAnnotate(def.data.underlyingReferable as? PsiElement, holder)
        return null
    }

    override fun visitClassFieldImpl(classFieldImpl: Concrete.ClassFieldImpl, params: Void?) {
        super.visitClassFieldImpl(classFieldImpl, params)
        doAnnotate(classFieldImpl.data as? PsiElement, holder)
    }

    override fun visitClassExt(expr: Concrete.ClassExtExpression, params: Void?): Void? {
        super.visitClassExt(expr, params)
        doAnnotate(expr.data as? PsiElement, holder)
        return null
    }

    override fun visitNew(expr: Concrete.NewExpression, params: Void?): Void? {
        super.visitNew(expr, params)
        if (expr.expression !is Concrete.ClassExtExpression) doAnnotate(expr.data as? PsiElement, holder)
        return null
    }
}