package org.vclang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.vclang.VcFileType
import org.vclang.refactoring.VcNamesValidator

class VcPsiFactory(private val project: Project) {

    fun createDefIdentifier(name: String): VcDefIdentifier =
            createFunction(name).defIdentifier ?: error("Failed to create def identifier: `$name`")

    fun createRefIdentifier(name: String): VcRefIdentifier =
            createStatCmd(name).nsCmdRoot?.refIdentifier
                    ?: error("Failed to create ref identifier: `$name`")

    fun createPrefixName(name: String): VcPrefixName {
        val needsPrefix = !VcNamesValidator().isPrefixName(name)
        return createLiteral(if (needsPrefix) "`$name" else name).prefixName
                ?: error("Failed to create prefix name: `$name`")
    }

    fun createInfixName(name: String): VcInfixName {
        val needsPrefix = !VcNamesValidator().isInfixName(name)
        return createExpression<VcBinOpExpr>("dummy ${if (needsPrefix) "`$name" else name} dummy")
                .binOpRightList
                .firstOrNull()
                ?.infixName
                ?: error("Failed to create infix name: `$name`")
    }

    fun createPostfixName(name: String): VcPostfixName {
        return createExpression<VcBinOpExpr>("dummy $name`")
                .binOpRightList
                .firstOrNull()
                ?.postfixName
                ?: error("Failed to create postfix name: `$name`")
    }

    private fun createFunction(
            name: String,
            teles: List<String> = emptyList(),
            expr: String? = null
    ): VcDefFunction {
        val code = buildString {
            append("\\function ")
            append(name)
            append(teles.joinToString(" ", " "))
            expr?.let { append(" : $expr") }
        }.trimEnd()
        return createFromText(code)?.childOfType() ?: error("Failed to create function: `$code`")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : VcExpr> createExpression(expr: String): T =
            createFunction("dummy", emptyList(), expr).expr as? T
                    ?: error("Failed to create expression: `$expr`")

    private fun createLiteral(literal: String): VcLiteral =
            createFunction("dummy", listOf(literal)).teleList.firstOrNull()?.childOfType()
                    ?: error("Failed to create literal: `$literal`")

    private fun createStatCmd(nsCmdRoot: String): VcStatCmd =
            createFromText("\\open $nsCmdRoot")?.childOfType()
                    ?: error("Failed to create stat cmd: `$nsCmdRoot`")

    private fun createFromText(code: String): VcFile? =
            PsiFileFactory.getInstance(project)
                    .createFileFromText("DUMMY.vc", VcFileType, code) as? VcFile
}
