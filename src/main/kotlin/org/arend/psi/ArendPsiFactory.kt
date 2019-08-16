package org.arend.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiParserFacade
import org.arend.ArendFileType
import org.arend.refactoring.ArendNamesValidator

class ArendPsiFactory(private val project: Project) {
    enum class StatCmdKind {OPEN, IMPORT}

    fun createDefIdentifier(name: String): ArendDefIdentifier =
            createFunction(name).defIdentifier ?: error("Failed to create def identifier: `$name`")

    fun createRefIdentifier(name: String): ArendRefIdentifier =
            createStatCmd(name).refIdentifierList.getOrNull(0)
                    ?: error("Failed to create ref identifier: `$name`")

    fun createLongName(name: String): ArendLongName =
            createImportCommand(name, StatCmdKind.IMPORT).statCmd?.longName ?: error("Failed to create long name: `$name`")

    fun createInfixName(name: String): ArendInfixArgument {
        val needsPrefix = !ArendNamesValidator.isInfixName(name)
        return createArgument("dummy ${if (needsPrefix) "`$name`" else name} dummy") as ArendInfixArgument
    }

    fun createPostfixName(name: String): ArendPostfixArgument {
        val needsPrefix = !ArendNamesValidator.isPostfixName(name)
        return createArgument("dummy ${if (needsPrefix) "`$name" else name}") as ArendPostfixArgument
    }

    fun createNameTele(name: String?, typeExpr: String, isExplicit: Boolean): ArendNameTele {
        val lparen = if (isExplicit) "(" else "{"
        val rparen = if (isExplicit) ")" else "}"
        return createFunction("dummy", listOf(lparen + (name ?: "_") + " : " + typeExpr) + rparen).nameTeleList.firstOrNull()
                ?: error("Failed to create name tele " + (name ?: ""))
    }

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

    fun createClause(expr: String): ArendClause {
        val code = "\\func foo => \\case goo \\with { | $expr => {?} }"
        return createFromText(code)?.childOfType() ?: error("Failed to create clause: `$code`")
    }

    fun createAtomPattern(expr: String): ArendAtomPatternOrPrefix {
        val code = "\\func foo (n : Nat) => \\case n \\with { | suc $expr => {?} }"
        return createFromText(code)?.childOfType() ?: error("Failed to create atom pattern/prefix: `$code`")
    }

    fun createCoClause(name: String, expr: String = "{?}"): ArendCoClause {
        val code = buildString {
            append("\\instance Dummy : Dummy\n")
            append("| $name => $expr")
        }
        return createFromText(code)?.childOfType() ?: error("Failed to create instance: `$code`")
    }

    fun createCoClauseInFunction(name: String, expr: String = "{?}"): ArendCoClause {
        val code = buildString {
            append("\\func Dummy : Dummy \\cowith\n")
            append("| $name => $expr")
        }
        return createFromText(code)?.childOfType() ?: error("Failed to create instance: `$code`")
    }

    fun createNestedCoClause(name: String): ArendCoClause {
        val code = buildString {
            append("\\instance Dummy : Dummy\n")
            append("| $name { }")
        }
        return createFromText(code)?.childOfType() ?: error("Failed to create instance: `$code`")
    }

    fun createPairOfBraces(): Pair<PsiElement, PsiElement> {
        val nestedCoClause = createNestedCoClause("foo")
        return Pair(nestedCoClause.lbrace!!, nestedCoClause.rbrace!!)
    }

    fun createPairOfParens(): Pair<PsiElement, PsiElement> {
        val statCmd = createStatCmd("foo")
        return Pair(statCmd.lparen!!, statCmd.rparen!!)
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

    fun createImportCommand(command : String, cmdKind: StatCmdKind): ArendStatement {
        val prefix = when (cmdKind) {
            StatCmdKind.OPEN -> "\\open "
            StatCmdKind.IMPORT -> "\\import "
        }
        val commands = createFromText(prefix + command)?.namespaceCommands
        if (commands != null && commands.size == 1) {
            return commands[0].parent as ArendStatement
        }
        error("Failed to create import command: $command")
    }

    fun createFromText(code: String): ArendFile? =
        PsiFileFactory.getInstance(project).createFileFromText("DUMMY.ard", ArendFileType, code) as? ArendFile

    fun createWhitespace(symbol: String): PsiElement {
        return PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(symbol)
    }

    fun createWhere(): ArendWhere {
        val code = "\\module Test \\where { }"
        return createFromText(code)?.childOfType() ?: error("Failed to create function: `$code`")
    }
}
