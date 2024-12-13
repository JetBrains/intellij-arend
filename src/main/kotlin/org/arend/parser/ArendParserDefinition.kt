package org.arend.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.arend.lexer.ArendLexerAdapter
import org.arend.naming.reference.Referable
import org.arend.parser.ParserMixin.*
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.doc.ArendDocCodeBlock
import org.arend.psi.doc.ArendDocReference
import org.arend.psi.doc.ArendDocReferenceText
import org.arend.psi.ext.*
import org.arend.psi.stubs.ArendFileStub
import org.arend.psi.ArendExpressionCodeFragmentElementType
import org.arend.refactoring.move.ArendLongNameCodeFragmentElementType

class ArendParserDefinition : ParserDefinition {

    override fun createLexer(project: Project): Lexer = ArendLexerAdapter()

    override fun getWhitespaceTokens(): TokenSet = AREND_WHITE_SPACES

    override fun getCommentTokens(): TokenSet = AREND_COMMENTS

    override fun getStringLiteralElements(): TokenSet = AREND_STRINGS

    override fun createParser(project: Project): PsiParser = ArendParser()

    override fun getFileNodeType(): IFileElementType = ArendFileStub.Type

    override fun createFile(viewProvider: FileViewProvider): PsiFile = ArendFile(viewProvider)

    override fun spaceExistenceTypeBetweenTokens(
            left: ASTNode,
            right: ASTNode
    ): ParserDefinition.SpaceRequirements = ParserDefinition.SpaceRequirements.MAY

    override fun createElement(node: ASTNode): PsiElement = when (val type = node.elementType) {
        DOC_CODE_BLOCK -> ArendDocCodeBlock(node)
        DOC_REFERENCE -> ArendDocReference(node)
        DOC_REFERENCE_TEXT -> ArendDocReferenceText(node)
        ALIAS -> ArendAlias(node)
        ALIAS_IDENTIFIER -> ArendAliasIdentifier(node)
        APP_EXPR -> ArendAppExpr(node)
        APP_PREFIX -> ArendAppPrefix(node)
        ARGUMENT_APP_EXPR -> ArendArgumentAppExpr(node)
        ARR_EXPR -> ArendArrExpr(node)
        AS_PATTERN -> ArendAsPattern(node)
        ATOM -> ArendAtom(node)
        ATOM_ARGUMENT -> ArendAtomArgument(node)
        ATOM_FIELDS_ACC -> ArendAtomFieldsAcc(node)
        FIELD_ACC -> ArendFieldAcc(node)
        ATOM_LEVEL_EXPR -> ArendAtomLevelExpr(node)
        ATOM_ONLY_LEVEL_EXPR -> ArendAtomOnlyLevelExpr(node)
        CASE_ARG -> ArendCaseArg(node)
        CASE_ARGUMENT -> ArendCaseArgument(node)
        CASE_EXPR -> ArendCaseExpr(node)
        CLASS_FIELD -> ArendClassField(node)
        CLASS_IMPLEMENT -> ArendClassImplement(node)
        CLASS_STAT -> ArendClassStat(node)
        CLAUSE -> ArendClause(node)
        CONSTRUCTOR -> ArendConstructor(node)
        CONSTRUCTOR_CLAUSE -> ArendConstructorClause(node)
        CO_CLAUSE -> ArendCoClause(node)
        CO_CLAUSE_BODY -> ArendFunctionBody(node, ArendFunctionBody.Kind.COCLAUSE)
        CO_CLAUSE_DEF -> ArendCoClauseDef(node)
        DATA_BODY -> ArendDataBody(node)
        DEF_CLASS -> ArendDefClass(node)
        DEF_DATA -> ArendDefData(node)
        DEF_FUNCTION -> ArendDefFunction(node)
        DEF_IDENTIFIER -> ArendDefIdentifier(node)
        DEF_INSTANCE -> ArendDefInstance(node)
        DEF_META -> ArendDefMeta(node)
        DEF_MODULE -> ArendDefModule(node)
        ELIM -> ArendElim(node)
        EXPR -> ArendExpr(node)
        FIELD_DEF_IDENTIFIER -> ArendFieldDefIdentifier(node)
        FIELD_TELE -> ArendFieldTele(node)
        FUNCTION_BODY -> ArendFunctionBody(node, ArendFunctionBody.Kind.FUNCTION)
        FUNCTION_CLAUSES -> ArendFunctionClauses(node)
        FUNCTION_KW -> ArendCompositeElementImpl(node)
        GOAL -> ArendGoal(node)
        H_LEVEL_IDENTIFIER -> ArendLevelIdentifier(node, Referable.RefKind.HLEVEL)
        H_LEVEL_PARAMS_SEQ, P_LEVEL_PARAMS_SEQ -> ArendLevelParamsSeq(node)
        IDENTIFIER_OR_UNKNOWN -> ArendIdentifierOrUnknown(node)
        IMPLICIT_ARGUMENT -> ArendImplicitArgument(node)
        INSTANCE_BODY -> ArendFunctionBody(node, ArendFunctionBody.Kind.INSTANCE)
        IP_NAME -> ArendIPName(node)
        LAM_ARGUMENT -> ArendLamArgument(node)
        LAM_EXPR -> ArendLamExpr(node)
        LAM_PARAM -> ArendLamParam(node)
        LAM_TELE, NAME_TELE -> ArendNameTele(node)
        LET_ARGUMENT -> ArendLetArgument(node)
        LET_CLAUSE -> ArendLetClause(node)
        LET_EXPR -> ArendLetExpr(node)
        LEVELS_EXPR -> ArendLevelsExpr(node)
        LEVEL_CMP -> ArendLevelCmp(node)
        LEVEL_EXPR -> ArendLevelExpr(node)
        LITERAL -> ArendLiteral(node)
        LOCAL_CO_CLAUSE -> ArendLocalCoClause(node)
        LONG_NAME -> ArendLongName(node)
        LONG_NAME_EXPR -> ArendLongNameExpr(node)
        MAYBE_ATOM_LEVEL_EXPR -> ArendMaybeAtomLevelExpr(node)
        MAYBE_ATOM_LEVEL_EXPRS -> ArendMaybeAtomLevelExprs(node)
        NAME_TELE_UNTYPED -> ArendNameTeleUntyped(node)
        NEW_ARG, NEW_EXPR -> ArendNewExpr(node)
        NS_ID -> ArendNsId(node)
        SC_ID -> ArendScId(node)
        NS_USING -> ArendNsUsing(node)
        ONLY_LEVEL_EXPR -> ArendOnlyLevelExpr(node)
        OVERRIDDEN_FIELD -> ArendOverriddenField(node)
        PATTERN -> ArendPattern(node)
        PI_EXPR -> ArendPiExpr(node)
        PREC -> ArendPrec(node)
        P_LEVEL_IDENTIFIER -> ArendLevelIdentifier(node, Referable.RefKind.PLEVEL)
        REF_IDENTIFIER -> ArendRefIdentifier(node)
        REPL_LINE -> ArendReplLine(node)
        RETURN_EXPR -> ArendReturnExpr(node)
        SET_UNIVERSE_APP_EXPR -> ArendSetUniverseAppExpr(node)
        SIGMA_EXPR -> ArendSigmaExpr(node)
        STAT -> ArendStat(node)
        STAT_CMD -> ArendStatCmd(node)
        SUPER_CLASS -> ArendSuperClass(node)
        TRUNCATED_UNIVERSE_APP_EXPR -> ArendTruncatedUniverseAppExpr(node)
        TUPLE -> ArendTuple(node)
        TUPLE_EXPR -> ArendTupleExpr(node)
        TYPED_EXPR -> ArendTypedExpr(node)
        TYPE_TELE -> ArendTypeTele(node)
        UNIVERSE_APP_EXPR -> ArendUniverseAppExpr(node)
        UNIVERSE_ATOM -> ArendUniverseAtom(node)
        UNIVERSE_EXPR -> ArendUniverseExpr(node)
        WHERE -> ArendWhere(node)
        WITH_BODY -> ArendWithBody(node)
        ACCESS_MOD -> ArendAccessMod(node)
        STAT_ACCESS_MOD -> ArendStatAccessMod(node)
        ArendExpressionCodeFragmentElementType -> ArendExpr(node)
        ArendLongNameCodeFragmentElementType -> ArendLongName(node)
        else -> throw AssertionError("Unknown element type: $type")
    }
}
