package org.vclang

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.error.ListErrorReporter
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrintVisitor
import org.vclang.psi.*
import org.vclang.psi.ext.PsiGlobalReferable

class VcDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        return if (element is PsiGlobalReferable) {
            buildString {
                getType(element)?.let { append("<b>$it</b> ") }
                append(element.textRepresentation())
                when (element) {
                    is VcDefFunction -> append (getDefFunctionInfo(element))
                }
                element.getContainingFile().originalFile.let {
                    append(" <i>defined in</i> ")
                    append((it as? VcFile)?.fullName ?: it.name)
                }
            }
        } else {
            null
        }
    }

    fun getDefFunctionInfo(element : VcDefFunction) : String? {
        val reporter = ListErrorReporter()
        val concreteElement = element.computeConcrete(reporter)
        val builder = StringBuilder()
        if (reporter.errorList.isEmpty()) {
            when (concreteElement) {
                is Concrete.FunctionDefinition -> {
                    builder.append(' ')
                    val printer = PrettyPrintVisitor(builder, 0, false)
                    printer.prettyPrintParameters(concreteElement.parameters, 0)
                    val resultType = concreteElement.resultType
                    if (resultType != null) {
                        builder.append(": ")
                        resultType.accept(printer, Precedence(0))
                    }
                }
            }
        }
        return builder.toString()
    }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? =
            generateDoc(element, originalElement)

    private fun getType(element: PsiElement): String? = when (element) {
        is VcDefClass -> "class"
        is VcClassField -> "class field"
        is VcDefClassView -> "class view"
        is VcDefInstance -> "class view instance"
        is VcClassImplement -> "implementation"
        is VcDefData -> "data"
        is VcConstructor -> "constructor"
        is VcDefFunction -> "function"
        else -> null
    }
}
