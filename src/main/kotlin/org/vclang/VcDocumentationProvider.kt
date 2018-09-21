package org.vclang

import com.google.common.html.HtmlEscapers
import com.intellij.codeInsight.documentation.DocumentationManagerUtil.createHyperlink
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.*
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.PsiReferable


private fun String.htmlEscape(): String = HtmlEscapers.htmlEscaper().escape(this)

class VcDocumentationProvider : AbstractDocumentationProvider() {
    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? =
        generateDoc(element, originalElement)

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?) =
        if (element !is PsiReferable) {
            null
        } else {
            buildString {
                wrap(DEFINITION_START, DEFINITION_END) {
                    generateDefinition(element)
                }

                wrap(CONTENT_START, CONTENT_END) {
                    generateContent(element, originalElement)
                }
            }
        }

    private fun StringBuilder.generateDefinition(element: PsiReferable) {
        wrapTag("b") {
            append(element.textRepresentation().htmlEscape())
        }

        append(getParametersList(element).htmlEscape())
        append(getResultType(element).htmlEscape())
    }

    private fun getParametersList(element: PsiReferable) =
        buildString {
            val parameters: List<Abstract.Parameter> =
                (element as? Abstract.ParametersHolder)?.parameters ?: emptyList()

            for (parameter in parameters) {
                if (parameter is PsiElement) {
                    append(" ${parameter.text}")
                }
            }
        }

    private fun getResultType(element: PsiReferable) =
        buildString {
            val resultType = element.psiElementType
            resultType?.let { append(" : ${it.text}") }
        }

    fun StringBuilder.generateContent(element: PsiElement, originalElement: PsiElement?) {
        wrapTag("em") {
            getType(element)?.let { append(it.htmlEscape()) }
        }

        getSourceFileName(element, originalElement)
            ?.run(String::htmlEscape)
            ?.let { fileName ->
                StringBuilder().also {
                    createHyperlink(it, fileName, fileName, false)
                    append(", defined in $it")
                }
            }
    }

    private fun getType(element: PsiElement): String? = when (element) {
        is VcDefClass -> if (element.fatArrow == null) "class" else "class synonym"
        is VcClassField, is VcFieldDefIdentifier -> "field"
        is VcClassFieldSyn -> "field synonym"
        is VcDefInstance -> "instance"
        is VcClassImplement -> "implementation"
        is VcDefData -> "data"
        is VcConstructor -> "data constructor"
        is VcDefFunction -> "function"
        is VcLetClause -> "let"
        is VcDefIdentifier -> if (element.parent is VcLetClause) "let" else "variable"
        else -> null
    }

    private fun getSourceFileName(element: PsiElement, originalElement: PsiElement?): String? {
        if (element is PsiLocatedReferable) {
            val file = element.containingFile.originalFile
            if (file != originalElement?.containingFile?.originalFile) {
                return (file as? VcFile)?.fullName ?: file.name
            }
        }

        return null
    }

    private inline fun StringBuilder.wrap(prefix: String, postfix: String, crossinline body: () -> Unit) {
        this.append(prefix)
        body()
        this.append(postfix)
    }

    private inline fun StringBuilder.wrapTag(tag: String, crossinline body: () -> Unit) {
        wrap("<$tag>", "</$tag>", body)
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
