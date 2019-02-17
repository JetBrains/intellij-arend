package org.arend.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiParserFacade
import org.arend.ArendFileType
import org.arend.refactoring.ArendNamesValidator

class ArendPsiFactory(private val project: Project) {

    fun createDefIdentifier(name: String): ArendDefIdentifier =
            createFunction(name).defIdentifier ?: error("Failed to create def identifier: `$name`")

    fun createRefIdentifier(name: String): ArendRefIdentifier =
            createStatCmd(name).refIdentifierList.getOrNull(0)
                    ?: error("Failed to create ref identifier: `$name`")

    fun createLongName(name: String): ArendLongName =
            createImportCommand(name).statCmd?.longName ?: error("Failed to create long name: `$name`")

    fun createInfixName(name: String): ArendInfixArgument {
        val needsPrefix = !ArendNamesValidator.isInfixName(name)
        return createArgument("dummy ${if (needsPrefix) "`$name`" else name} dummy") as ArendInfixArgument
    }

    fun createPostfixName(name: String): ArendPostfixArgument {
        val needsPrefix = !ArendNamesValidator.isPostfixName(name)
        return createArgument("dummy ${if (needsPrefix) "`$name" else name}") as ArendPostfixArgument
    }

    fun createNameTele(name: String?, typeExpr: String): ArendNameTele =
        createFunction("dummy", listOf("(" + (name ?: "_") + " : " + typeExpr) + ")").nameTeleList.firstOrNull()
                ?: error("Failed to create name tele " + (name ?: ""))

    private fun createFunction(
            name: String,
            teles: List<String> = emptyList(),
            expr: String? = null
    ): ArendDefFunction {
        val code = buildString {
            append("\\func ")
            append(name)
            append(teles.joinToString(" ", " "))
            expr?.let { append(" : $expr") }
        }.trimEnd()
        return createFromText(code)?.childOfType() ?: error("Failed to create function: `$code`")
    }

    fun createCoClause(name: String, expr: String): ArendCoClauses {
        val code = buildString {
            append("\\instance Dummy : Dummy\n")
            append("| $name => $expr")
        }
        return createFromText(code)?.childOfType() ?: error("Failed to create instance: `$code`")
    }

    fun createNestedCoClause(name: String): ArendCoClauses {
        val code = buildString {
            append("\\instance Dummy : Dummy\n")
            append("| $name { }")
        }
        return createFromText(code)?.childOfType() ?: error("Failed to create instance: `$code`")
    }

    fun createPairOfBraces(): Pair<PsiElement, PsiElement> {
        val nestedCoClause = createNestedCoClause("foo").coClauseList.first()
        return Pair(nestedCoClause.getLbrace()!!, nestedCoClause.rbrace!!)
    }

    private fun createArgument(expr: String): ArendArgument =
        ((createFunction("dummy", emptyList(), expr).returnExpr?.expr as ArendNewExpr?)?.appExpr as ArendArgumentAppExpr?)?.argumentList?.let { it[0] }
            ?: error("Failed to create expression: `$expr`")

    fun createLiteral(expr: String): ArendLiteral =
        ((createFunction("dummy", emptyList(), expr).returnExpr?.expr as ArendNewExpr?)?.appExpr as ArendArgumentAppExpr?)?.atomFieldsAcc?.atom?.literal
            ?: error("Failed to create literal: `$expr`")

    private fun createStatCmd(name: String): ArendStatCmd =
        createFromText("\\open X \\hiding ($name)")?.childOfType()
            ?: error("Failed to create stat cmd: `$name`")

    fun createImportCommand(command : String): ArendStatement {
        val commands = createFromText("\\import $command")?.namespaceCommands
        if (commands != null && commands.size == 1) {
            return commands[0].parent as ArendStatement
        }
        error("Failed to create import command: \\import $command")
    }

    fun createFromText(code: String): ArendFile? =
        PsiFileFactory.getInstance(project).createFileFromText("DUMMY.ard", ArendFileType, code) as? ArendFile

    fun createWhitespace(symbol: String): PsiElement {
        return PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(symbol)
    }
}
