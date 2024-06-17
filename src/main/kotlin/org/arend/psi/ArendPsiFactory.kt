package org.arend.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiParserFacade
import org.arend.ArendFileTypeInstance
import org.arend.InjectionTextLanguage
import org.arend.psi.ext.*

class ArendPsiFactory(
    private val project: Project,
    private val fileName: String = "DUMMY.ard"
) {
    enum class StatCmdKind {OPEN, IMPORT}

    fun injected(text: String): PsiFile =
        psiFactory().createFileFromText(fileName, InjectionTextLanguage.INSTANCE, text)

    fun createDefIdentifier(name: String): ArendDefIdentifier =
        createFunction(name).defIdentifier ?: error("Failed to create def identifier: `$name`")

    fun createRefIdentifier(name: String): ArendRefIdentifier =
        createStatCmd(name).refIdentifierList.getOrNull(0)
            ?: error("Failed to create identifier: $name")

    fun createAliasIdentifier(name: String): ArendAliasIdentifier =
        createFromText("\\func foo \\alias $name")?.childOfType()
            ?: error("Failed to create alias identifier: `$name`")

    fun createLongName(name: String): ArendLongName =
        createImportCommand(name, StatCmdKind.IMPORT).namespaceCommand?.longName ?: error("Failed to create long name: `$name`")

    fun createLetExpression(letClause : String, expressionToWrap : String) : ArendLetExpr =
        createFromText("\\func foo => \\let \n $letClause \n\\in $expressionToWrap")?.childOfType()
                ?: error("Failed to create let expression: `$letClause`, `$expressionToWrap`")

    private fun createIPName(name: String): ArendIPName =
        createFunction("dummy", emptyList(), name).childOfType()
            ?: error("Failed to create identifier: $name")

    fun createInfixName(name: String) = createIPName("`$name`")

    fun createPostfixName(name: String) = createIPName("`$name")

    fun createNameTele(name: String?, typeExpr: String?, isExplicit: Boolean): ArendNameTele {
        val lparen = if (isExplicit) "(" else "{"
        val rparen = if (isExplicit) ")" else "}"
        return createFunction("dummy", listOf(lparen + (name ?: "_") + if (typeExpr != null) " : $typeExpr" else "") + rparen).parameters.firstOrNull()
                ?: error("Failed to create name tele " + (name ?: ""))
    }

    fun createFieldTele(name: String?, typeExpr: String, isExplicit: Boolean): ArendFieldTele {
        val lparen = if (isExplicit) "(" else "{"
        val rparen = if (isExplicit) ")" else "}"
        return createFromText("\\class Dummy $lparen ${name ?: "_"} : $typeExpr $rparen")!!.childOfType()!!
    }

    fun createTypeTele(name: String?, typeExpr: String, isExplicit: Boolean): ArendTypeTele {
        val lparen = if (isExplicit) "(" else "{"
        val rparen = if (isExplicit) ")" else "}"
        return createFromText("\\data Dummy $lparen ${name ?: "_"}${if (name?.isEmpty() == true) "" else " : "}$typeExpr $rparen")!!.childOfType()!!
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

    fun createFunctionKeyword(keyword: String): ArendCompositeElement =
        createFromText("$keyword foo => 0")?.childOfType<ArendDefFunction>()?.functionKw ?: error("Failed to create keyword $keyword")

    fun createInstanceKeyword(keyword: String): PsiElement =
        createFromText("$keyword foo => 0")?.childOfType<ArendDefInstance>()?.firstChild ?: error("Failed to create keyword $keyword")

    fun createClause(expr: String, createWithEmptyExpression: Boolean = false): ArendClause {
        val code = "\\func foo => \\case goo \\with { | $expr ${if (createWithEmptyExpression) "" else "=> {?}"} }"
        return createFromText(code)?.childOfType() ?: error("Failed to create clause: `$code`")
    }

    fun createExpressionMaybe(expr: String): ArendExpr? =
        createFromText("\\func foo => $expr")?.childOfType()

    fun createExpression(expr: String) =
        createExpressionMaybe(expr) ?: error("Failed to create expr: `$expr`")

    fun createWithBody(): ArendWithBody =
        (createExpression("\\case _ \\with {}") as? ArendCaseExpr)?.withBody ?: error("Failed to create withBody")

    fun createFunctionClauses(instance: Boolean = false): ArendFunctionClauses {
        val code = "\\${if (instance) "instance" else "func"} foo (a : Nat) : Nat\n  | 0 => {?}"
        return createFromText(code)?.childOfType() ?: error("Failed to create clause: `$code`")
    }

    fun createPattern(expr: String): ArendPattern {
        val code = "\\func foo (n : Nat) => \\case n \\with { | $expr => {?} }"
        return createFromText(code)?.childOfType() ?: error("Failed to create pattern: `$code`")
    }

    fun createCoClause(name: String, expr: String = "{?}"): ArendCoClause {
        val code = buildString {
            append("\\instance Dummy : Dummy\n")
            append("| $name => $expr")
        }
        return createFromText(code)?.childOfType() ?: error("Failed to create instance: `$code`")
    }

    fun createLocalCoClause(name: String, expr: String? = "{?}"): ArendLocalCoClause {
        val code = buildString {
            append("\\func foo => \\new Foo {\n")
            append("| $name => $expr\n }")
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

    fun createLam(teles: List<String>, expr: String): ArendLamExpr {
        val code = buildString {
            append("\\lam")
            append(teles.joinToString(" ", " "))
            append(" => ")
            append(expr)
        }.trimEnd()
        return createFromText(code)?.childOfType() ?: error("Failed to create lambda: `$code`")
    }

    fun createPairOfBraces(): Pair<PsiElement, PsiElement> {
        val nestedCoClause = createNestedCoClause("foo")
        return Pair(nestedCoClause.lbrace!!, nestedCoClause.rbrace!!)
    }

    fun createPairOfParens(): Pair<PsiElement, PsiElement> {
        val statCmd = createStatCmd("foo")
        return Pair(statCmd.lparen!!, statCmd.rparen!!)
    }

    fun createStatCmd(name: String): ArendStatCmd =
        createFromText("\\open X \\hiding ($name)")?.childOfType() ?: error("Failed to create stat cmd: `$name`")

    fun createImportCommand(command : String, cmdKind: StatCmdKind): ArendStat {
        val prefix = when (cmdKind) {
            StatCmdKind.OPEN -> "\\open "
            StatCmdKind.IMPORT -> "\\import "
        }
        val statements = createFromText(prefix + command)?.statements
        if (statements != null && statements.size == 1) {
            return statements[0]
        }
        error("Failed to create import command: $command")
    }

    fun createFromText(code: String): ArendFile? =
        psiFactory().createFileFromText(fileName, ArendFileTypeInstance, code) as? ArendFile

    private fun psiFactory() = PsiFileFactory.getInstance(project)

    fun createWhitespace(symbol: String): PsiElement =
        PsiParserFacade.getInstance(project).createWhiteSpaceFromText(symbol)

    fun createPipe(): PsiElement = createFromText("\\data D | con")?.childOfType<ArendDataBody>()?.firstChild ?: error("Failed to create '|'")

    fun createWhere(): ArendWhere = createFromText("\\module Test \\where { }")?.childOfType() ?: error("Failed to create '\\where'")

    fun createCoClauseBody(): ArendFunctionBody = createFromText("\\func foo (n : Nat) : R \\cowith\n | f{-caret-} x \\with")?.childOfType<ArendFunctionBody>()?.childOfType(true) ?: error("Failed to create coClauseBody keyword")

    fun createClassStat(): ArendClassStat = createFromText("\\class C { \\func bar => 101 }")?.childOfType() ?: error("Failed to create classStat")

    fun createCaseArg(caseArg: String): ArendCaseArg? = createFromText("\\func foo => \\case $caseArg, lol \\with {} ")?.childOfType()

    fun createColon() = createCaseArg("dummy : Nat")?.colon ?: error("Failed to create ':'")
}
