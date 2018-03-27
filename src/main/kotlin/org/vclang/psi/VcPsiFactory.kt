package org.vclang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiParserFacade
import org.vclang.VcFileType
import org.vclang.refactoring.VcNamesValidator

class VcPsiFactory(private val project: Project) {

    fun createDefIdentifier(name: String): VcDefIdentifier =
            createFunction(name).defIdentifier ?: error("Failed to create def identifier: `$name`")

    fun createRefIdentifier(name: String): VcRefIdentifier =
            createStatCmd(name).refIdentifierList.getOrNull(0)
                    ?: error("Failed to create ref identifier: `$name`")

    fun createInfixName(name: String): VcInfixArgument {
        val needsPrefix = !VcNamesValidator.isInfixName(name)
        return createArgument("dummy ${if (needsPrefix) "`$name`" else name} dummy") as VcInfixArgument
    }

    fun createPostfixName(name: String): VcPostfixArgument {
        val needsPrefix = !VcNamesValidator.isPostfixName(name)
        return createArgument("dummy ${if (needsPrefix) "`$name" else name}") as VcPostfixArgument
    }

    private fun createFunction(
            name: String,
            teles: List<String> = emptyList(),
            expr: String? = null
    ): VcDefFunction {
        val code = buildString {
            append("\\func ")
            append(name)
            append(teles.joinToString(" ", " "))
            expr?.let { append(" : $expr") }
        }.trimEnd()
        return createFromText(code)?.childOfType() ?: error("Failed to create function: `$code`")
    }

    private fun createArgument(expr: String): VcArgument =
        ((createFunction("dummy", emptyList(), expr).expr as VcNewExpr?)?.appExpr as VcArgumentAppExpr?)?.argumentList?.let { it[0] }
            ?: error("Failed to create expression: `$expr`")

    fun createLiteral(expr: String): VcLiteral =
        ((createFunction("dummy", emptyList(), expr).expr as VcNewExpr?)?.appExpr as VcArgumentAppExpr?)?.atomFieldsAcc?.atom?.literal
            ?: error("Failed to create literal: `$expr`")

    private fun createStatCmd(name: String): VcStatCmd =
        createFromText("\\open X \\hiding ($name)")?.childOfType()
            ?: error("Failed to create stat cmd: `$name`")

    fun createFromText(code: String): VcFile? =
        PsiFileFactory.getInstance(project).createFileFromText("DUMMY.vc", VcFileType, code) as? VcFile

    fun createWhitespace(symbol: String): PsiElement {
        return PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(symbol)
    }
}
