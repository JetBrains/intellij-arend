package org.vclang

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.naming.reference.converter.IdReferableConverter
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.ConcreteBuilder
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrintVisitor
import org.vclang.psi.*
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.VcLetClauseImplMixin
import org.vclang.psi.ext.impl.*

class VcDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?) =
        when (element) {
            is PsiLocatedReferable -> buildString {
                getType(element)?.let { append("<b>$it</b> ") }
                append(toHTML(element.textRepresentation()))
                append(when (element) {
                    is FunctionDefinitionAdapter -> printHeader(element.parameters, element.resultType)
                    is ClassFieldAdapter -> printHeader(element.parameters, element.resultType)
                    is ClassDefinitionAdapter -> printHeader(element.fieldTeleList, null)
                    is ConstructorAdapter -> printHeader(element.parameters, null)
                    is DataDefinitionAdapter -> printHeader(element.parameters, null)
                    else -> ""
                })
                element.getContainingFile().originalFile.let {
                    append(" <i>defined in</i> ")
                    append((it as? VcFile)?.fullName ?: it.name)
                }
            }
            is VcLetClauseImplMixin -> buildString {
                append("<b> var </b>")
                append(element.name)
                append(printHeader(element.parameters, element.resultType))
            }
            else -> null
        }

    private fun printHeader(parameters : List<Abstract.Parameter>, resultType : Abstract.Expression?) =
        toHTML(buildString {
            append (printParameters(parameters))
            if (resultType != null) {
                append(" : ")
                append(printExpression(resultType))
            }
        })

    private fun printParameters(parameters: List<Abstract.Parameter>): String? {
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

    /* fun getConstructorInfo (element : VcConstructor) : String? {
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

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? =
            generateDoc(element, originalElement)

    private fun getType(element: PsiElement): String? = when (element) {
        is VcDefClass -> if (element.fatArrow == null) "class" else "class synonym"
        is VcClassField -> "class field"
        is VcClassFieldSyn -> "class field synonym"
        is VcDefInstance -> "class instance"
        is VcClassImplement -> "implementation"
        is VcDefData -> "data"
        is VcConstructor -> "constructor"
        is VcDefFunction -> "function"
        else -> null
    }

    companion object {
        fun toHTML (s : String?) : String? = s?.replace("&", "&amp")?.replace("<", "&lt")?.replace(">", "&gt")
    }

}
