package org.vclang

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.*
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.PsiReferable


private fun toHTML(s : String?): String? = s?.replace("&", "&amp")?.replace("<", "&lt")?.replace(">", "&gt")

class VcDocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(element: PsiElement, originalElement: PsiElement?) =
        if (element is PsiReferable) {
            buildString {
                getType(element)?.let { append("<b>$it</b> ") }
                append(toHTML(element.textRepresentation()))
                append(printHeader((element as? Abstract.ParametersHolder)?.parameters ?: emptyList(), element.psiElementType))
                if (element is PsiLocatedReferable) {
                    val file = element.containingFile.originalFile
                    if (file != originalElement?.containingFile?.originalFile) {
                        append(" defined in ")
                        append((file as? VcFile)?.fullName ?: file.name)
                    }
                }
            }
        } else {
            null
        }

    private fun printHeader(parameters: List<Abstract.Parameter>, resultType: PsiElement?) =
        toHTML(buildString {
            for (parameter in parameters) {
                if (parameter is PsiElement) {
                    append(' ')
                    append(parameter.text)
                }
            }
            if (resultType != null) {
                append(" : ")
                append(resultType.text)
            }
        })

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? =
        generateDoc(element, originalElement)

    private fun getType(element: PsiElement): String? = when (element) {
        is VcDefClass -> if (element.fatArrow == null) "class" else "class synonym"
        is VcClassField, is VcFieldDefIdentifier -> "field"
        is VcClassFieldSyn -> "field synonym"
        is VcDefInstance -> "instance"
        is VcClassImplement -> "implementation"
        is VcDefData -> "data"
        is VcConstructor -> "data cons"
        is VcDefFunction -> "func"
        is VcLetClause -> "let"
        is VcDefIdentifier -> if (element.parent is VcLetClause) "let" else "var"
        else -> null
    }

    /*  private fun printParameters(parameters: List<Abstract.Parameter>): String? {
        val list = ConcreteBuilder.convertParams(IdReferableConverter.INSTANCE, parameters)
        return if (list != null && list.isNotEmpty()) {
            val builder = StringBuilder()
            val printer = PrettyPrintVisitor(builder, 0, false)
            builder.append(' ')
            printer.prettyPrintParameters(list, 0)
            builder.toString()
        } else ""
    }

    private fun printExpression(element: Abstract.Expression): String? {
        val expression = ConcreteBuilder.convertExpression(IdReferableConverter.INSTANCE, element)
        return if (expression != null) {
            val builder = StringBuilder()
            val printer = PrettyPrintVisitor(builder, 0, false)
            expression.accept(printer, Precedence(0))
            builder.toString()
        } else ""
    }

    fun getConstructorInfo (element : VcConstructor) : String? {
        val reporter = ListErrorReporter()
        val concreteElement = element.computeConcrete(reporter)
        val builder = StringBuilder()
        if (reporter.errorList.isEmpty()) {
            when (concreteElement) {
                is Concrete.Constructor -> {
                    builder.append(' ')
                    val printer = PrettyPrintVisitor(builder, 0, false)
                    printer.prettyPrintParameters(concreteElement.parameters, 0)
                }
            }
        }
        return builder.toString()
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
    } */
}
