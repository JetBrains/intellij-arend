package org.vclang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.vclang.VcFileType
import org.vclang.refactoring.VcNamesValidator

class VcPsiFactory(private val project: Project) {

    fun createDefIdentifier(name: String): VcDefIdentifier =
            createFunction(name).defIdentifier ?: error("Failed to create def identifier: `$name`")

    fun createRefIdentifier(name: String): VcRefIdentifier =
            createStatCmd(name).refIdentifierList.getOrNull(0)
                    ?: error("Failed to create ref identifier: `$name`")

    fun createInfixName(name: String): VcInfixArgument {
        val needsPrefix = !VcNamesValidator().isInfixName(name)
        return createExpression("dummy ${if (needsPrefix) "`$name`" else name} dummy")
    }

    fun createPostfixName(name: String): VcPostfixArgument {
        val needsPrefix = !VcNamesValidator().isPostfixName(name)
        return createExpression("dummy ${if (needsPrefix) "`$name" else name}")
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
            createFunction("dummy", listOf(literal)).nameTeleList.firstOrNull()?.childOfType()
                    ?: error("Failed to create literal: `$literal`")

    private fun createStatCmd(name: String): VcStatCmd =
            createFromText("\\open X \\hiding ($name)")?.childOfType()
                    ?: error("Failed to create stat cmd: `$name`")

    private fun createFromText(code: String): VcFile? =
            PsiFileFactory.getInstance(project)
                    .createFileFromText("DUMMY.vc", VcFileType, code) as? VcFile
}
