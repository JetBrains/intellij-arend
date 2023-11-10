package org.arend.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType
import org.arend.ArendLanguage
import org.arend.parser.ParserMixin.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ArendKeywordSection.*

class ArendTokenType(debugName: String) : IElementType(debugName, ArendLanguage.INSTANCE)

enum class ArendKeywordSection(val sectionName: String) {
    OPEN_SECTION("open-commands"),
    IMPORT_SECTION("import-commands"),
    TRUNCATED_SECTION("truncation"),
    CONS_SECTION("constructor-synonyms"),
    LEMMA_SECTION("lemmas"),
    SFUNC_SECTION("sfunc"),
    CLASSIFYING_SECTION("classifying-fields"),
    STRICT_SECTION("strict-parameters"),
    ALIAS_SECTION("aliases"),
    PROPERTY_SECTION("properties"),
    OVERRIDE_SECTION("override"),
    DEFAULT_SECTION("default"),
    EXTENDS_SECTION("extensions"),
    MODULE_SECTION("modules"),
    INSTANCES_SECTION("instances"),
    LEVELS_SECTION("level-polymorphism"),
    PLEVELS_SECTION("level-parameters"),
    WITH_SECTION("pattern-matching"),
    ELIM_SECTION("elim"),
    COWITH_SECTION("copattern-matching"),
    WHERE_SECTION("where-blocks"),
    INFIX_SECTION("infix-operators"),
    FIX_SECTION("precedence"),
    THIS_SECTION("this"),
    LETS_SECTION("strict-let-expressions"),
    HAVE_SECTION("have"),
    SCASE_SECTION("scase"),
    LP_SECTION("level-polymorphism")
}

enum class ArendKeyword(val type: IElementType, val section: ArendKeywordSection?) {
    OPEN(OPEN_KW, OPEN_SECTION),
    IMPORT(IMPORT_KW, IMPORT_SECTION),
    HIDING(HIDING_KW, OPEN_SECTION),
    AS(AS_KW, OPEN_SECTION),
    USING(USING_KW, OPEN_SECTION),
    TRUNCATED(TRUNCATED_KW, TRUNCATED_SECTION),
    DATA(DATA_KW, null),
    CONS(CONS_KW, CONS_SECTION),
    FUNC(FUNC_KW, null),
    LEMMA(LEMMA_KW, LEMMA_SECTION),
    AXIOM(AXIOM_KW, LEMMA_SECTION),
    SFUNC(SFUNC_KW, SFUNC_SECTION),
    TYPE(TYPE_KW, null),
    CLASS(CLASS_KW, null),
    RECORD(RECORD_KW, null),
    META(META_KW, null),
    CLASSIFYING(CLASSIFYING_KW, CLASSIFYING_SECTION),
    NO_CLASSIFYING(NO_CLASSIFYING_KW, CLASSIFYING_SECTION),
    STRICT(STRICT_KW, STRICT_SECTION),
    ALIAS(ALIAS_KW, ALIAS_SECTION),
    FIELD(FIELD_KW, null),
    PROPERTY(PROPERTY_KW, PROPERTY_SECTION),
    OVERRIDE(OVERRIDE_KW, OVERRIDE_SECTION),
    DEFAULT(DEFAULT_KW, DEFAULT_SECTION),
    EXTENDS(EXTENDS_KW, EXTENDS_SECTION),
    MODULE(MODULE_KW, MODULE_SECTION),
    INSTANCE(INSTANCE_KW, INSTANCES_SECTION),
    USE(USE_KW, null),
    COERCE(COERCE_KW, null),
    LEVEL(LEVEL_KW, null),
    LEVELS(LEVELS_KW, LEVELS_SECTION),
    PLEVELS(PLEVELS_KW, PLEVELS_SECTION),
    HLEVELS(HLEVELS_KW, PLEVELS_SECTION),
    BOX(BOX_KW, null),
    EVAL(EVAL_KW, SFUNC_SECTION),
    PEVAL(PEVAL_KW, SFUNC_SECTION),
    WITH(WITH_KW, WITH_SECTION),
    ELIM(ELIM_KW, ELIM_SECTION),
    COWITH(COWITH_KW, COWITH_SECTION),
    WHERE(WHERE_KW, WHERE_SECTION),
    INFIX(INFIX_NON_KW, INFIX_SECTION),
    INFIX_LEFT(INFIX_LEFT_KW, INFIX_SECTION),
    INFIX_RIGHT(INFIX_RIGHT_KW, INFIX_SECTION),
    FIX(NON_ASSOC_KW, FIX_SECTION),
    FIX_LEFT(LEFT_ASSOC_KW, FIX_SECTION),
    FIX_RIGHT(RIGHT_ASSOC_KW, FIX_SECTION),
    NEW(NEW_KW, null),
    THIS(THIS_KW, THIS_SECTION),
    PI(PI_KW, null),
    SIGMA(SIGMA_KW, null),
    LAM(LAM_KW, null),
    LET(LET_KW, null),
    LETS(LETS_KW, LETS_SECTION),
    HAVE(HAVE_KW, HAVE_SECTION),
    HAVES(HAVES_KW, HAVE_SECTION),
    IN(IN_KW, null),
    CASE(CASE_KW, null),
    SCASE(SCASE_KW, SCASE_SECTION),
    RETURN(RETURN_KW, null),
    LP(LP_KW, LP_SECTION),
    LH(LH_KW, LP_SECTION),
    SUC(SUC_KW, LP_SECTION),
    MAX(MAX_KW, LP_SECTION),
    OO(OO_KW, LP_SECTION),
    PROP(PROP_KW, null),
    SET(ArendElementTypes.SET, null),
    UNIVERSE(ArendElementTypes.UNIVERSE, null),
    TRUNCATED_UNIVERSE(ArendElementTypes.TRUNCATED_UNIVERSE, null),
    PRIVATE(PRIVATE_KW, null),
    PROTECTED(PROTECTED_KW, null)
    ;

    companion object {
        private val keywordTypes = values().associateBy { it.type }

        fun PsiElement?.toArendKeyword() = keywordTypes[this.elementType]

        fun PsiElement?.isArendKeyword(): Boolean = ArendKeyword.keywordTypes.contains(this.elementType)

        val AREND_KEYWORDS = initTokenSet(values().map { it.type })
    }
}

fun initTokenSet(collection: Collection<IElementType>): TokenSet {
    var tokenSet = TokenSet.create()
    collection.forEach {
        tokenSet = TokenSet.orSet(tokenSet, TokenSet.create(it))
    }
    return tokenSet
}

val AREND_COMMENTS: TokenSet = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT, DOC_TEXT)

val AREND_NAMES: TokenSet = TokenSet.create(ID, INFIX, POSTFIX)

val AREND_WHITE_SPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

val AREND_STRINGS: TokenSet = TokenSet.create(STRING)

val AREND_GOALS: TokenSet = TokenSet.create(TGOAL, UNDERSCORE, APPLY_HOLE)

val AREND_DOC_TOKENS: TokenSet = TokenSet.create(DOC_SINGLE_LINE_START, DOC_START, DOC_END, DOC_INLINE_CODE_BORDER,
    DOC_TEXT, DOC_CODE, DOC_CODE_LINE, DOC_PARAGRAPH_SEP, DOC_LBRACKET, DOC_RBRACKET, DOC_CODE_BLOCK_BORDER, DOC_CODE_BLOCK,
    DOC_REFERENCE, DOC_REFERENCE_TEXT)