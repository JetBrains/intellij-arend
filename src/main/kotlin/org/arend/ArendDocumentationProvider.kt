package org.arend

import com.google.common.html.HtmlEscapers
import com.intellij.codeInsight.documentation.DocumentationManagerUtil.createHyperlink
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.arend.term.abs.Abstract
import org.arend.psi.*
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable


private fun String.htmlEscape(): String = HtmlEscapers.htmlEscaper().escape(this)

class ArendDocumentationProvider : AbstractDocumentationProvider() {
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

                val docComments = extractDocComments(element)
                if (!docComments.isEmpty()) {
                    wrap(CONTENT_START, CONTENT_END) {
                        append(docComments)
                    }
                }

                wrap(CONTENT_START, CONTENT_END) {
                    generateContent(element, originalElement)
                }
            }
        }

    private fun getElementJustBeforeReferable(element: PsiReferable): PsiElement? {
        if (element.parent is ArendClassStat || element.parent is ArendStatement) {
            return element.parent.prevSibling
        }
        return null
    }

    private fun extractDocComments(element: PsiReferable): String =
            buildString {
                var prevElement = getElementJustBeforeReferable(element)
                if (prevElement is PsiWhiteSpace) {
                    prevElement = prevElement.prevSibling
                }
                if (prevElement is PsiComment && prevElement.tokenType == ArendElementTypes.BLOCK_COMMENT_END) {
                    prevElement = prevElement.prevSibling
                    while (prevElement is PsiComment && prevElement.tokenType != ArendElementTypes.BLOCK_DOC_COMMENT_START) {
                        if (prevElement.tokenType != ArendElementTypes.BLOCK_COMMENT) {
                            append(prevElement.text)
                        }
                        prevElement = prevElement.prevSibling
                    }
                }
                if (prevElement is PsiComment && prevElement.tokenType == ArendElementTypes.LINE_DOC_TEXT
                    && prevElement.prevSibling is PsiComment && (prevElement.prevSibling as PsiComment).tokenType == ArendElementTypes.LINE_DOC_COMMENT_START) {
                    append(prevElement.text)
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
        is ArendDefClass -> if (element.fatArrow == null) "class" else "class synonym"
        is ArendClassField, is ArendFieldDefIdentifier -> "field"
        is ArendClassFieldSyn -> "field synonym"
        is ArendDefInstance -> "instance"
        is ArendClassImplement -> "implementation"
        is ArendDefData -> "data"
        is ArendConstructor -> "data constructor"
        is ArendDefFunction -> "function"
        is ArendLetClause -> "let"
        is ArendDefIdentifier -> if (element.parent is ArendLetClause) "let" else "variable"
        else -> null
    }

    private fun getSourceFileName(element: PsiElement, originalElement: PsiElement?): String? {
        if (element is PsiLocatedReferable) {
            val file = element.containingFile.originalFile
            if (file != originalElement?.containingFile?.originalFile) {
                return (file as? ArendFile)?.fullName ?: file.name
            }
        }

        return null
    }

    private inline fun StringBuilder.wrap(prefix: String, postfix: String, crossinline body: () -> Unit) {
        this.append(prefix)
        body()
        this.append(postfix)
    }

    private inline fun StringBuilder.wrapNonempty(prefix: String, postfix: String, crossinline body: () -> Unit) {
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

    private fun printExpression(anchor: Abstract.Expression): String? {
        val expression = ConcreteBuilder.convertExpression(IdReferableConverter.INSTANCE, anchor)
        return if (expression != null) {
            val builder = StringBuilder()
            val printer = PrettyPrintVisitor(builder, 0, false)
            expression.accept(printer, Precedence(0))
            builder.toString()
        } else ""
    }

    fun getConstructorInfo (anchor : ArendConstructor) : String? {
        val reporter = ListErrorReporter()
        val concreteElement = anchor.computeConcrete(reporter)
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

    fun getDefFunctionInfo(anchor : ArendDefFunction) : String? {
        val reporter = ListErrorReporter()
        val concreteElement = anchor.computeConcrete(reporter)
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
